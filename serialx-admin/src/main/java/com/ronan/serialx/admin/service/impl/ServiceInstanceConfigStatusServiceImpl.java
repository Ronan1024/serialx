package com.ronan.serialx.admin.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service 实例配置状态 Redis 查询服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceInstanceConfigStatusServiceImpl implements ServiceInstanceConfigStatusService {

    private static final long SCAN_COUNT = 200L;

    /**
     * Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * JSON 序列化组件。
     */
    private final ObjectMapper objectMapper;

    /**
     * 实例存活判定窗口。
     */
    @Value("${serialx.admin.instance-status.alive-timeout:90s}")
    private Duration aliveTimeout = Duration.ofSeconds(90);

    @Override
    public List<ServiceInstanceConfigStatusDO> listAll() {
        List<ServiceInstanceConfigStatusDO> statuses = new ArrayList<>();
        statuses.addAll(listInstanceHeartbeatStatuses());
        statuses.addAll(listByStatusKeyPattern(ServiceInstanceRedisConstants.INSTANCE_NAMESPACE_KEY_PATTERN));
        statuses.addAll(listByStatusKeyPattern(ServiceInstanceRedisConstants.LEGACY_INSTANCE_STATUS_KEY_PATTERN));
        return statuses.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                this::buildStatusKey,
                                status -> status,
                                this::chooseLatestStatus,
                                LinkedHashMap::new),
                        item -> new ArrayList<>(item.values())))
                .stream()
                .map(this::markOfflineIfNeeded)
                .sorted((left, right) -> {
                    if (left.getLastReportTime() == null || right.getLastReportTime() == null) {
                        return 0;
                    }
                    return right.getLastReportTime().compareTo(left.getLastReportTime());
                })
                .toList();
    }

    @Override
    public List<ServiceInstanceConfigStatusDO> listByNamespaceCode(String namespaceCode) {
        return listAll().stream()
                .filter(status -> Objects.equals(namespaceCode, status.getNamespaceCode()))
                .toList();
    }

    private List<ServiceInstanceConfigStatusDO> listByStatusKeyPattern(String keyPattern) {
        Set<String> keys = scanKeys(keyPattern);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(this::readStatusByKey)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<ServiceInstanceConfigStatusDO> listInstanceHeartbeatStatuses() {
        Set<String> keys = scanKeys(ServiceInstanceRedisConstants.INSTANCE_KEY_PATTERN);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(this::readInstanceInfoByKey)
                .filter(Objects::nonNull)
                .map(this::toNodeStatus)
                .toList();
    }

    private ServiceInstanceConfigStatusDO readStatusByKey(String key) {
        Map<Object, Object> hash = readHash(key);
        if (!hash.isEmpty()) {
            return toStatus(hash);
        }
        String statusJson = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(statusJson)) {
            return null;
        }
        return readStatus(statusJson);
    }

    private JsonNode readInstanceInfoByKey(String key) {
        Map<Object, Object> hash = readHash(key);
        if (!hash.isEmpty()) {
            return objectMapper.valueToTree(hash);
        }
        String instanceJson = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(instanceJson)) {
            return null;
        }
        return readInstanceInfo(instanceJson);
    }

    private Map<Object, Object> readHash(String key) {
        var hashOperations = stringRedisTemplate.opsForHash();
        if (hashOperations == null) {
            return Map.of();
        }
        Map<Object, Object> hash = hashOperations.entries(key);
        return hash == null ? Map.of() : hash;
    }

    private ServiceInstanceConfigStatusDO readStatus(String statusJson) {
        try {
            return objectMapper.readValue(statusJson, ServiceInstanceConfigStatusDO.class);
        } catch (JsonProcessingException ex) {
            log.warn("deserialize service instance status failed, statusJson={}", statusJson, ex);
            return null;
        }
    }

    private JsonNode readInstanceInfo(String instanceJson) {
        try {
            return objectMapper.readTree(instanceJson);
        } catch (JsonProcessingException ex) {
            log.warn("deserialize service instance info failed, instanceJson={}", instanceJson, ex);
            return null;
        }
    }

    private ServiceInstanceConfigStatusDO toStatus(Map<Object, Object> hash) {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(readText(hash, ServiceInstanceRedisConstants.Fields.INSTANCE_ID));
        status.setHost(readText(hash, ServiceInstanceRedisConstants.Fields.HOST));
        status.setNamespaceCode(readText(hash, ServiceInstanceRedisConstants.Fields.NAMESPACE_CODE));
        status.setLoadedVersion(readInteger(hash, ServiceInstanceRedisConstants.Fields.LOADED_VERSION));
        status.setRuntimeStatus(readText(hash, ServiceInstanceRedisConstants.Fields.RUNTIME_STATUS));
        status.setLastReportTime(readLocalDateTime(hash, ServiceInstanceRedisConstants.Fields.LAST_REPORT_TIME));
        status.setLastOfflineTime(readLocalDateTime(hash, ServiceInstanceRedisConstants.Fields.LAST_OFFLINE_TIME));
        return status;
    }

    private ServiceInstanceConfigStatusDO toNodeStatus(JsonNode instanceInfo) {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(readText(instanceInfo, ServiceInstanceRedisConstants.Fields.INSTANCE_ID));
        status.setHost(buildHost(instanceInfo));
        status.setRuntimeStatus(readText(instanceInfo, ServiceInstanceRedisConstants.Fields.STATUS));
        status.setLastReportTime(readLocalDateTime(instanceInfo, ServiceInstanceRedisConstants.Fields.LAST_REPORT_TIME));
        status.setLastOfflineTime(readLocalDateTime(instanceInfo, ServiceInstanceRedisConstants.Fields.LAST_OFFLINE_TIME));
        return status;
    }

    private ServiceInstanceConfigStatusDO markOfflineIfNeeded(ServiceInstanceConfigStatusDO status) {
        if (isHeartbeatAlive(status.getInstanceId())) {
            return status;
        }
        LocalDateTime lastReportTime = status.getLastReportTime();
        status.setRuntimeStatus(ServiceInstanceRedisConstants.OFFLINE);
        if (status.getLastOfflineTime() == null) {
            status.setLastOfflineTime(lastReportTime == null ? LocalDateTime.now() : lastReportTime.plus(aliveTimeout));
        }
        return status;
    }

    private boolean isHeartbeatAlive(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            return false;
        }
        String heartbeatKey = String.format(ServiceInstanceRedisConstants.INSTANCE_HEARTBEAT_KEY_FORMAT, instanceId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(heartbeatKey));
    }

    private Set<String> scanKeys(String keyPattern) {
        if (stringRedisTemplate.getConnectionFactory() == null) {
            return stringRedisTemplate.keys(keyPattern);
        }
        return stringRedisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new java.util.HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(keyPattern)
                    .count(SCAN_COUNT)
                    .build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    private String buildStatusKey(ServiceInstanceConfigStatusDO status) {
        return safeValue(status.getInstanceId()) + "|" + safeValue(status.getNamespaceCode());
    }

    private ServiceInstanceConfigStatusDO chooseLatestStatus(
            ServiceInstanceConfigStatusDO left,
            ServiceInstanceConfigStatusDO right) {
        LocalDateTime leftReportTime = left.getLastReportTime();
        LocalDateTime rightReportTime = right.getLastReportTime();
        if (leftReportTime == null) {
            return right;
        }
        if (rightReportTime == null) {
            return left;
        }
        return rightReportTime.isAfter(leftReportTime) ? right : left;
    }

    private String buildHost(JsonNode instanceInfo) {
        if (instanceInfo == null) {
            return null;
        }
        String ip = readText(instanceInfo, ServiceInstanceRedisConstants.Fields.IP);
        String port = readText(instanceInfo, ServiceInstanceRedisConstants.Fields.PORT);
        if (!StringUtils.hasText(port)) {
            return ip;
        }
        return ServiceInstanceRedisConstants.SERVICE_NAME + "/" + ip + ":" + port;
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDateTime readLocalDateTime(JsonNode node, String fieldName) {
        String value = readText(node, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    private String readText(Map<Object, Object> hash, String fieldName) {
        Object value = hash.get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private Integer readInteger(Map<Object, Object> hash, String fieldName) {
        String value = readText(hash, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private LocalDateTime readLocalDateTime(Map<Object, Object> hash, String fieldName) {
        String value = readText(hash, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
