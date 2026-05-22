package com.ronan.serialx.admin.manager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ronan.serialx.admin.dto.CallStatsClientResponse;
import com.ronan.serialx.admin.dto.CallStatsErrorResponse;
import com.ronan.serialx.admin.dto.CallStatsInstanceResponse;
import com.ronan.serialx.admin.dto.CallStatsNamespaceResponse;
import com.ronan.serialx.admin.dto.CallStatsQueryRequest;
import com.ronan.serialx.admin.dto.CallStatsResponse;
import com.ronan.serialx.admin.dto.CallStatsSummaryResponse;
import com.ronan.serialx.admin.dto.CallStatsTrendPointResponse;
import com.ronan.serialx.admin.service.NamespaceService;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.infra.entity.IdCallStatsMinuteDO;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import com.ronan.serialx.infra.mapper.IdCallStatsMinuteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 调用统计聚合查询编排。
 */
@Component
@RequiredArgsConstructor
public class CallStatsManager {

    private static final String SUCCESS = "SUCCESS";

    private static final Duration DEFAULT_RANGE = Duration.ofHours(1);

    private static final int TOP_LIMIT = 10;

    private final IdCallStatsMinuteMapper idCallStatsMinuteMapper;

    private final ServiceInstanceConfigStatusService serviceInstanceConfigStatusService;

    private final NamespaceService namespaceService;

    /**
     * 查询调用统计页面数据。
     */
    public CallStatsResponse query(CallStatsQueryRequest request) {
        TimeRange timeRange = resolveTimeRange(request);
        List<IdCallStatsMinuteDO> records = idCallStatsMinuteMapper.selectList(Wrappers
                .lambdaQuery(IdCallStatsMinuteDO.class)
                .ge(IdCallStatsMinuteDO::getStatMinute, timeRange.startTime())
                .le(IdCallStatsMinuteDO::getStatMinute, timeRange.endTime())
                .eq(StringUtils.hasText(request.getNamespaceCode()), IdCallStatsMinuteDO::getNamespaceCode, request.getNamespaceCode())
                .eq(StringUtils.hasText(request.getInstanceId()), IdCallStatsMinuteDO::getInstanceId, request.getInstanceId())
                .eq(StringUtils.hasText(request.getClientId()), IdCallStatsMinuteDO::getClientId, request.getClientId())
                .like(StringUtils.hasText(request.getClientName()), IdCallStatsMinuteDO::getClientName, request.getClientName()));

        Aggregate total = new Aggregate();
        Map<LocalDateTime, Aggregate> trendMap = new HashMap<>();
        Map<String, Aggregate> instanceMap = new HashMap<>();
        Map<String, Aggregate> namespaceMap = new HashMap<>();
        Map<String, Aggregate> clientMap = new HashMap<>();
        Map<String, Aggregate> errorMap = new HashMap<>();
        records.forEach(e -> {
            total.add(e);
            trendMap.computeIfAbsent(e.getStatMinute(), ignored -> new Aggregate()).add(e);
            instanceMap.computeIfAbsent(e.getInstanceId(), ignored -> new Aggregate()).add(e);
            namespaceMap.computeIfAbsent(e.getNamespaceCode(), ignored -> new Aggregate()).add(e);
            clientMap.computeIfAbsent(clientKey(e), ignored -> new Aggregate()).add(e);
            if (!SUCCESS.equals(e.getResult())) {
                errorMap.computeIfAbsent(e.getErrorCode(), ignored -> new Aggregate()).add(e);
            }
        });

        Map<String, NodeSnapshot> nodeSnapshotMap = loadNodeSnapshots(records);
        List<CallStatsInstanceResponse> instances = buildInstances(instanceMap, nodeSnapshotMap, timeRange);
        CallStatsSummaryResponse summary = buildSummary(total, instances, timeRange);

        return CallStatsResponse.builder()
                .startTime(timeRange.startTime())
                .endTime(timeRange.endTime())
                .summary(summary)
                .trend(buildTrend(trendMap, timeRange))
                .instances(instances)
                .namespaces(buildNamespaces(namespaceMap, records, timeRange))
                .clients(buildClients(clientMap, timeRange))
                .errors(buildErrors(errorMap))
                .build();
    }

    private TimeRange resolveTimeRange(CallStatsQueryRequest request) {
        Instant now = Instant.now();
        Instant endInstant = request.getEndTime() == null ? now : request.getEndTime();
        Instant startInstant = request.getStartTime() == null ? endInstant.minus(DEFAULT_RANGE) : request.getStartTime();
        if (startInstant.isAfter(endInstant)) {
            Instant tmp = startInstant;
            startInstant = endInstant;
            endInstant = tmp;
        }
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime startTime = LocalDateTime.ofInstant(startInstant, zoneId).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime endTime = LocalDateTime.ofInstant(endInstant, zoneId).truncatedTo(ChronoUnit.MINUTES);
        return new TimeRange(startTime, endTime);
    }

    private CallStatsSummaryResponse buildSummary(
            Aggregate total,
            List<CallStatsInstanceResponse> instances,
            TimeRange timeRange) {
        long totalRequests = total.totalRequests();
        double avgLoadRate = instances.stream()
                .map(CallStatsInstanceResponse::getLoadRate)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);
        long onlineCount = instances.stream()
                .filter(item -> ServiceInstanceRedisConstants.ACTIVE.equals(item.getRuntimeStatus()))
                .count();
        return CallStatsSummaryResponse.builder()
                .totalRequests(totalRequests)
                .successRequests(total.successRequests())
                .failedRequests(total.failedRequests())
                .totalIds(total.idCount())
                .avgQps(avgQps(totalRequests, timeRange))
                .avgLatencyMs(avgLatency(total.latencyTotalMs(), totalRequests))
                .maxLatencyMs(total.latencyMaxMs())
                .errorRate(errorRate(total.failedRequests(), totalRequests))
                .onlineInstanceCount(onlineCount)
                .avgLoadRate(round(avgLoadRate))
                .build();
    }

    private List<CallStatsTrendPointResponse> buildTrend(Map<LocalDateTime, Aggregate> trendMap, TimeRange timeRange) {
        List<CallStatsTrendPointResponse> points = new ArrayList<>();
        LocalDateTime current = timeRange.startTime();
        while (!current.isAfter(timeRange.endTime())) {
            Aggregate aggregate = trendMap.getOrDefault(current, new Aggregate());
            long totalRequests = aggregate.totalRequests();
            points.add(CallStatsTrendPointResponse.builder()
                    .time(current)
                    .totalRequests(totalRequests)
                    .successRequests(aggregate.successRequests())
                    .failedRequests(aggregate.failedRequests())
                    .avgLatencyMs(avgLatency(aggregate.latencyTotalMs(), totalRequests))
                    .build());
            current = current.plusMinutes(1);
        }
        return points;
    }

    private List<CallStatsInstanceResponse> buildInstances(
            Map<String, Aggregate> instanceMap,
            Map<String, NodeSnapshot> nodeSnapshotMap,
            TimeRange timeRange) {
        Set<String> instanceIds = new HashSet<>();
        instanceIds.addAll(instanceMap.keySet());
        instanceIds.addAll(nodeSnapshotMap.keySet());
        return instanceIds.stream()
                .map(instanceId -> {
                    Aggregate aggregate = instanceMap.getOrDefault(instanceId, new Aggregate());
                    NodeSnapshot snapshot = nodeSnapshotMap.getOrDefault(instanceId, NodeSnapshot.empty(instanceId));
                    long totalRequests = aggregate.totalRequests();
                    double currentQps = avgQps(totalRequests, timeRange);
                    Long maxQps = aggregate.maxQps();
                    Double loadRate = maxQps == null || maxQps <= 0 ? null : round(currentQps * 100 / maxQps);
                    return CallStatsInstanceResponse.builder()
                            .instanceId(instanceId)
                            .host(firstText(aggregate.host(), snapshot.host()))
                            .runtimeStatus(snapshot.runtimeStatus())
                            .lastReportTime(snapshot.lastReportTime())
                            .namespaceCount(snapshot.namespaceCount())
                            .laggingNamespaceCount(snapshot.laggingNamespaceCount())
                            .requestCount(totalRequests)
                            .successCount(aggregate.successRequests())
                            .failedCount(aggregate.failedRequests())
                            .idCount(aggregate.idCount())
                            .currentQps(currentQps)
                            .avgLatencyMs(avgLatency(aggregate.latencyTotalMs(), totalRequests))
                            .maxLatencyMs(aggregate.latencyMaxMs())
                            .errorRate(errorRate(aggregate.failedRequests(), totalRequests))
                            .maxQps(maxQps)
                            .loadRate(loadRate)
                            .build();
                })
                .sorted(Comparator
                        .comparing(CallStatsInstanceResponse::getCurrentQps)
                        .reversed()
                        .thenComparing(CallStatsInstanceResponse::getInstanceId, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<CallStatsNamespaceResponse> buildNamespaces(
            Map<String, Aggregate> namespaceMap,
            List<IdCallStatsMinuteDO> records,
            TimeRange timeRange) {
        Map<String, Long> instanceCountMap = records.stream()
                .filter(e -> StringUtils.hasText(e.getNamespaceCode()))
                .collect(Collectors.groupingBy(IdCallStatsMinuteDO::getNamespaceCode, Collectors.mapping(IdCallStatsMinuteDO::getInstanceId, Collectors.toSet())))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));
        return namespaceMap.entrySet().stream()
                .map(entry -> {
                    Aggregate aggregate = entry.getValue();
                    long totalRequests = aggregate.totalRequests();
                    return CallStatsNamespaceResponse.builder()
                            .namespaceCode(entry.getKey())
                            .requestCount(totalRequests)
                            .successCount(aggregate.successRequests())
                            .failedCount(aggregate.failedRequests())
                            .idCount(aggregate.idCount())
                            .avgQps(avgQps(totalRequests, timeRange))
                            .avgLatencyMs(avgLatency(aggregate.latencyTotalMs(), totalRequests))
                            .maxLatencyMs(aggregate.latencyMaxMs())
                            .errorRate(errorRate(aggregate.failedRequests(), totalRequests))
                            .instanceCount(instanceCountMap.getOrDefault(entry.getKey(), 0L))
                            .build();
                })
                .sorted(Comparator.comparingLong(CallStatsNamespaceResponse::getRequestCount).reversed())
                .limit(TOP_LIMIT)
                .toList();
    }

    private List<CallStatsClientResponse> buildClients(Map<String, Aggregate> clientMap, TimeRange timeRange) {
        return clientMap.values().stream()
                .map(aggregate -> {
                    long totalRequests = aggregate.totalRequests();
                    return CallStatsClientResponse.builder()
                            .clientId(aggregate.clientId())
                            .clientName(aggregate.clientName())
                            .requestCount(totalRequests)
                            .successCount(aggregate.successRequests())
                            .failedCount(aggregate.failedRequests())
                            .idCount(aggregate.idCount())
                            .avgQps(avgQps(totalRequests, timeRange))
                            .avgLatencyMs(avgLatency(aggregate.latencyTotalMs(), totalRequests))
                            .maxLatencyMs(aggregate.latencyMaxMs())
                            .errorRate(errorRate(aggregate.failedRequests(), totalRequests))
                            .build();
                })
                .sorted(Comparator.comparingLong(CallStatsClientResponse::getRequestCount).reversed())
                .limit(TOP_LIMIT)
                .toList();
    }

    private List<CallStatsErrorResponse> buildErrors(Map<String, Aggregate> errorMap) {
        long totalFailed = errorMap.values().stream().mapToLong(Aggregate::failedRequests).sum();
        return errorMap.entrySet().stream()
                .map(entry -> CallStatsErrorResponse.builder()
                        .errorCode(entry.getKey())
                        .count(entry.getValue().failedRequests())
                        .percentage(errorRate(entry.getValue().failedRequests(), totalFailed))
                        .build())
                .sorted(Comparator.comparingLong(CallStatsErrorResponse::getCount).reversed())
                .toList();
    }

    private Map<String, NodeSnapshot> loadNodeSnapshots(List<IdCallStatsMinuteDO> records) {
        List<ServiceInstanceConfigStatusDO> statuses = serviceInstanceConfigStatusService.listAll();
        Map<String, NamespaceDO> namespaceMap = namespaceService.listByCodes(statuses.stream()
                        .map(ServiceInstanceConfigStatusDO::getNamespaceCode)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(NamespaceDO::getNamespaceCode, Function.identity(), (left, right) -> left));
        Map<String, NodeSnapshot> snapshots = statuses.stream()
                .filter(status -> StringUtils.hasText(status.getInstanceId()))
                .collect(Collectors.groupingBy(ServiceInstanceConfigStatusDO::getInstanceId))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> buildNodeSnapshot(entry.getKey(), entry.getValue(), namespaceMap)));
        records.stream()
                .filter(minuteDO -> StringUtils.hasText(minuteDO.getInstanceId()))
                .forEach(minuteDO -> snapshots.putIfAbsent(minuteDO.getInstanceId(), NodeSnapshot.fromStats(minuteDO)));
        return snapshots;
    }

    private NodeSnapshot buildNodeSnapshot(
            String instanceId,
            List<ServiceInstanceConfigStatusDO> statuses,
            Map<String, NamespaceDO> namespaceMap) {
        boolean active = statuses.stream().anyMatch(status -> ServiceInstanceRedisConstants.ACTIVE.equals(status.getRuntimeStatus()));
        String runtimeStatus = active ? ServiceInstanceRedisConstants.ACTIVE : ServiceInstanceRedisConstants.OFFLINE;
        String host = statuses.stream()
                .map(ServiceInstanceConfigStatusDO::getHost)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        LocalDateTime lastReportTime = statuses.stream()
                .map(ServiceInstanceConfigStatusDO::getLastReportTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        int namespaceCount = (int) statuses.stream()
                .map(ServiceInstanceConfigStatusDO::getNamespaceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .count();
        int laggingNamespaceCount = (int) statuses.stream()
                .filter(status -> isLagging(status, namespaceMap.get(status.getNamespaceCode())))
                .count();
        return new NodeSnapshot(instanceId, host, runtimeStatus, lastReportTime, namespaceCount, laggingNamespaceCount);
    }

    private boolean isLagging(ServiceInstanceConfigStatusDO status, NamespaceDO namespace) {
        return namespace != null
                && namespace.getCurrentVersion() != null
                && status.getLoadedVersion() != null
                && status.getLoadedVersion() < namespace.getCurrentVersion();
    }

    private String clientKey(IdCallStatsMinuteDO minuteDO) {
        return safe(minuteDO.getClientId()) + "|" + safe(minuteDO.getClientName());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstText(String left, String right) {
        return StringUtils.hasText(left) ? left : right;
    }

    private double avgQps(long totalRequests, TimeRange timeRange) {
        long seconds = Math.max(1L, Duration.between(timeRange.startTime(), timeRange.endTime().plusMinutes(1)).toSeconds());
        return round((double) totalRequests / seconds);
    }

    private double avgLatency(long latencyTotalMs, long totalRequests) {
        if (totalRequests <= 0) {
            return 0D;
        }
        return round((double) latencyTotalMs / totalRequests);
    }

    private double errorRate(long failedRequests, long totalRequests) {
        if (totalRequests <= 0) {
            return 0D;
        }
        return round((double) failedRequests * 100 / totalRequests);
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private record TimeRange(LocalDateTime startTime, LocalDateTime endTime) {
    }

    private record NodeSnapshot(
            String instanceId,
            String host,
            String runtimeStatus,
            LocalDateTime lastReportTime,
            int namespaceCount,
            int laggingNamespaceCount) {

        static NodeSnapshot empty(String instanceId) {
            return new NodeSnapshot(instanceId, null, ServiceInstanceRedisConstants.OFFLINE, null, 0, 0);
        }

        static NodeSnapshot fromStats(IdCallStatsMinuteDO idCallStatsMinuteDO) {
            return new NodeSnapshot(idCallStatsMinuteDO.getInstanceId(), idCallStatsMinuteDO.getHost(), ServiceInstanceRedisConstants.OFFLINE, null, 0, 0);
        }
    }

    private static class Aggregate {

        private long successRequests;

        private long failedRequests;

        private long idCount;

        private long latencyTotalMs;

        private long latencyMaxMs;

        private Long maxQps;

        private String host;

        private String clientId;

        private String clientName;

        void add(IdCallStatsMinuteDO callStatsMinuteDO) {
            long requestCount = value(callStatsMinuteDO.getRequestCount());
            if (SUCCESS.equals(callStatsMinuteDO.getResult())) {
                successRequests += requestCount;
            } else {
                failedRequests += requestCount;
            }
            idCount += value(callStatsMinuteDO.getIdCount());
            latencyTotalMs += value(callStatsMinuteDO.getLatencyTotalMs());
            latencyMaxMs = Math.max(latencyMaxMs, value(callStatsMinuteDO.getLatencyMaxMs()));
            if (callStatsMinuteDO.getMaxQps() != null && callStatsMinuteDO.getMaxQps() > 0) {
                maxQps = callStatsMinuteDO.getMaxQps();
            }
            if (StringUtils.hasText(callStatsMinuteDO.getHost())) {
                host = callStatsMinuteDO.getHost();
            }
            if (StringUtils.hasText(callStatsMinuteDO.getClientId())) {
                clientId = callStatsMinuteDO.getClientId();
            }
            if (StringUtils.hasText(callStatsMinuteDO.getClientName())) {
                clientName = callStatsMinuteDO.getClientName();
            }
        }

        long totalRequests() {
            return successRequests + failedRequests;
        }

        long successRequests() {
            return successRequests;
        }

        long failedRequests() {
            return failedRequests;
        }

        long idCount() {
            return idCount;
        }

        long latencyTotalMs() {
            return latencyTotalMs;
        }

        long latencyMaxMs() {
            return latencyMaxMs;
        }

        Long maxQps() {
            return maxQps;
        }

        String host() {
            return host;
        }

        String clientId() {
            return clientId;
        }

        String clientName() {
            return clientName;
        }

        private long value(Long value) {
            return value == null ? 0L : value;
        }
    }
}
