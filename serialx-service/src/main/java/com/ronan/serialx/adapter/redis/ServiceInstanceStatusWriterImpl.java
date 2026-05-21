package com.ronan.serialx.adapter.redis;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.domain.model.ServiceInstanceInfo;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Service 实例运行态 Redis 写入器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceInstanceStatusWriterImpl {

    private final StringRedisTemplate stringRedisTemplate;

    private final ServiceInstanceProperties serviceInstanceProperties;

    /**
     * 写入节点心跳。
     */
    public void saveOnline(ServiceInstanceInfo instanceInfo) {
        String key = String.format(ServiceInstanceRedisConstants.INSTANCE_KEY_FORMAT, instanceInfo.getInstanceId());
        setHashValue(key, toInstanceHash(instanceInfo));
        refreshHeartbeat(instanceInfo.getInstanceId(), instanceInfo.getRuntimeId());
    }

    /**
     * 写入节点离线快照。
     */
    public void saveOffline(ServiceInstanceInfo instanceInfo) {
        String key = String.format(ServiceInstanceRedisConstants.INSTANCE_KEY_FORMAT, instanceInfo.getInstanceId());
        setHashValue(key, toInstanceHash(instanceInfo));
        removeHeartbeat(instanceInfo.getInstanceId());
    }

    /**
     * 原子抢占实例心跳，用于启动时校验 instanceId 唯一性。
     */
    public boolean tryAcquireHeartbeat(String instanceId, String runtimeIdValue) {
        String heartbeatKey = String.format(
                ServiceInstanceRedisConstants.INSTANCE_HEARTBEAT_KEY_FORMAT,
                instanceId);
        String runtimeId = StringUtils.hasText(runtimeIdValue) ? runtimeIdValue : instanceId;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(heartbeatKey, runtimeId, heartbeatTtl()));
    }

    /**
     * 写入 Namespace 加载状态。
     */
    public void saveOrUpdate(ServiceInstanceConfigStatusDO status) {
        String namespaceCode = StringUtils.hasText(status.getNamespaceCode()) ? status.getNamespaceCode() : ServiceInstanceRedisConstants.NODE_NAMESPACE;
        String key = String.format(ServiceInstanceRedisConstants.INSTANCE_NAMESPACE_KEY_FORMAT, status.getInstanceId(), namespaceCode);
        setHashValue(key, toNamespaceStatusHash(status));
    }

    private void setHashValue(String key, Map<String, String> value) {
        stringRedisTemplate.opsForHash().putAll(key, value);
        stringRedisTemplate.expire(key, serviceInstanceProperties.getStatusTtl());
        log.debug("write service instance status to redis, key={}", key);
    }

    private void refreshHeartbeat(String instanceId, String runtimeIdValue) {
        String heartbeatKey = String.format(
                ServiceInstanceRedisConstants.INSTANCE_HEARTBEAT_KEY_FORMAT,
                instanceId);
        String runtimeId = StringUtils.hasText(runtimeIdValue) ? runtimeIdValue : instanceId;
        stringRedisTemplate.opsForValue().set(heartbeatKey, runtimeId, heartbeatTtl());
    }

    private void removeHeartbeat(String instanceId) {
        String heartbeatKey = String.format(
                ServiceInstanceRedisConstants.INSTANCE_HEARTBEAT_KEY_FORMAT,
                instanceId);
        stringRedisTemplate.delete(heartbeatKey);
    }

    private Duration heartbeatTtl() {
        return serviceInstanceProperties.getReportInterval().multipliedBy(3);
    }

    private Map<String, String> toInstanceHash(ServiceInstanceInfo instanceInfo) {
        Map<String, String> map = new LinkedHashMap<>();
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.INSTANCE_ID, instanceInfo.getInstanceId());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.RUNTIME_ID, instanceInfo.getRuntimeId());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.HOST_NAME, instanceInfo.getHostName());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.IP, instanceInfo.getIp());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.PORT, instanceInfo.getPort());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.STATUS, instanceInfo.getStatus());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.CREATE_TIME, formatTime(instanceInfo.getCreateTime()));
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.LAST_REPORT_TIME, formatTime(instanceInfo.getLastReportTime()));
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.LAST_OFFLINE_TIME, formatTime(instanceInfo.getLastOfflineTime()));
        return map;
    }

    private Map<String, String> toNamespaceStatusHash(ServiceInstanceConfigStatusDO status) {
        Map<String, String> map = new LinkedHashMap<>();
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.INSTANCE_ID, status.getInstanceId());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.HOST, status.getHost());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.NAMESPACE_CODE, status.getNamespaceCode());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.LOADED_VERSION, status.getLoadedVersion());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.RUNTIME_STATUS, status.getRuntimeStatus());
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.LAST_REPORT_TIME, formatTime(status.getLastReportTime()));
        putIfNotNull(map, ServiceInstanceRedisConstants.Fields.LAST_OFFLINE_TIME, formatTime(status.getLastOfflineTime()));
        return map;
    }

    private void putIfNotNull(Map<String, String> map, String key, Object value) {
        if (value != null) {
            map.put(key, String.valueOf(value));
        }
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
