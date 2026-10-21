package com.ronan.serialx.admin.manager;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ronan.serialx.admin.dto.ServiceInstanceNodeListResponse;
import com.ronan.serialx.admin.dto.ServiceInstanceNodeNamespaceResponse;
import com.ronan.serialx.admin.dto.ServiceInstanceNodeResponse;
import com.ronan.serialx.admin.dto.ServiceInstanceNodeSummaryResponse;
import com.ronan.serialx.admin.service.NamespaceService;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Service 节点业务编排。
 */
@Component
@RequiredArgsConstructor
public class ServiceInstanceNodeManager {

    /**
     * Service 实例配置状态服务。
     */
    private final ServiceInstanceConfigStatusService serviceInstanceConfigStatusService;

    /**
     * Namespace 数据访问服务。
     */
    private final NamespaceService namespaceService;

    /**
     * 查询 Service 节点列表。
     */
    public ServiceInstanceNodeListResponse listNodes() {
        List<ServiceInstanceConfigStatusDO> statuses = serviceInstanceConfigStatusService.listAll().stream().filter(this::isVisibleStatus).toList();
        Map<String, NamespaceDO> namespaceMap = namespaceService.listByCodes(statuses.stream()
                        .map(ServiceInstanceConfigStatusDO::getNamespaceCode).filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(NamespaceDO::getNamespaceCode, Function.identity(), (left, right) -> left));
        List<ServiceInstanceNodeResponse> nodes = statuses.stream()
                .collect(Collectors.groupingBy(this::buildNodeKey))
                .values()
                .stream()
                .map(item -> toNodeResponse(item, namespaceMap))
                .sorted(Comparator
                        .comparing((ServiceInstanceNodeResponse item) -> isActive(item.getRuntimeStatus()))
                        .reversed()
                        .thenComparing(ServiceInstanceNodeResponse::getLastReportTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return ServiceInstanceNodeListResponse.builder()
                .summary(toSummary(nodes))
                .nodes(nodes)
                .build();
    }

    private boolean isActive(String runtimeStatus) {
        return ServiceInstanceRedisConstants.ACTIVE.equals(runtimeStatus);
    }

    private ServiceInstanceNodeSummaryResponse toSummary(List<ServiceInstanceNodeResponse> nodes) {
        long onlineCount = nodes.stream()
                .filter(item -> isActive(item.getRuntimeStatus()))
                .count();
        long laggingNamespaceCount = nodes.stream()
                .mapToLong(ServiceInstanceNodeResponse::getLaggingNamespaceCount)
                .sum();
        return ServiceInstanceNodeSummaryResponse.builder()
                .totalCount(nodes.size())
                .onlineCount(onlineCount)
                .offlineCount(nodes.size() - onlineCount)
                .laggingNamespaceCount(laggingNamespaceCount)
                .build();
    }

    private ServiceInstanceNodeResponse toNodeResponse(
            List<ServiceInstanceConfigStatusDO> statuses,
            Map<String, NamespaceDO> namespaceMap) {
        ServiceInstanceConfigStatusDO primary = choosePrimaryStatus(statuses);
        List<ServiceInstanceNodeNamespaceResponse> namespaces = statuses.stream()
                .filter(status -> status.getNamespaceCode() != null)
                .map(status -> toNamespaceResponse(status, namespaceMap.get(status.getNamespaceCode())))
                .sorted(Comparator.comparing(
                        ServiceInstanceNodeNamespaceResponse::getNamespaceCode,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
        int laggingNamespaceCount = (int) namespaces.stream()
                .filter(ServiceInstanceNodeNamespaceResponse::isLagging)
                .count();
        LocalDateTime lastReportTime = statuses.stream()
                .map(ServiceInstanceConfigStatusDO::getLastReportTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime lastOfflineTime = statuses.stream()
                .map(ServiceInstanceConfigStatusDO::getLastOfflineTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        String host = statuses.stream()
                .map(ServiceInstanceConfigStatusDO::getHost)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(primary.getHost());
        boolean active = statuses.stream().anyMatch(status -> isActive(status.getRuntimeStatus()));
        return ServiceInstanceNodeResponse.builder()
                .instanceId(primary.getInstanceId())
                .serviceName(ServiceInstanceRedisConstants.SERVICE_NAME)
                .host(host)
                .runtimeStatus(active ? ServiceInstanceRedisConstants.ACTIVE : ServiceInstanceRedisConstants.OFFLINE)
                .namespaceCount(namespaces.size())
                .laggingNamespaceCount(laggingNamespaceCount)
                .lastReportTime(lastReportTime)
                .lastOfflineTime(active ? null : lastOfflineTime)
                .namespaces(namespaces)
                .build();
    }

    private ServiceInstanceConfigStatusDO choosePrimaryStatus(List<ServiceInstanceConfigStatusDO> statuses) {
        return statuses.stream()
                .sorted(Comparator
                        .comparing((ServiceInstanceConfigStatusDO item) -> isActive(item.getRuntimeStatus()))
                        .reversed()
                        .thenComparing(ServiceInstanceConfigStatusDO::getLastReportTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElse(statuses.getFirst());
    }

    private ServiceInstanceNodeNamespaceResponse toNamespaceResponse(
            ServiceInstanceConfigStatusDO status,
            NamespaceDO namespace) {
        Integer currentVersion = namespace == null ? null : namespace.getCurrentVersion();
        boolean lagging = currentVersion != null
                && status.getLoadedVersion() != null
                && status.getLoadedVersion() < currentVersion;
        return ServiceInstanceNodeNamespaceResponse.builder()
                .namespaceCode(status.getNamespaceCode())
                .namespaceName(namespace == null ? null : namespace.getNamespaceName())
                .loadedVersion(status.getLoadedVersion())
                .currentVersion(currentVersion)
                .runtimeStatus(status.getRuntimeStatus())
                .lagging(lagging)
                .lastReportTime(status.getLastReportTime())
                .lastOfflineTime(status.getLastOfflineTime())
                .build();
    }

    private String buildNodeKey(ServiceInstanceConfigStatusDO status) {
        return safeValue(status.getInstanceId());
    }

    private boolean isVisibleStatus(ServiceInstanceConfigStatusDO status) {
        if (isActive(status.getRuntimeStatus())) {
            return true;
        }
        return status.getInstanceId() != null
                && !status.getInstanceId().isBlank()
                && status.getHost() != null
                && !status.getHost().isBlank();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
