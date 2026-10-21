package com.ronan.serialx.adapter.redis;

import static com.ronan.serialx.common.node.NodeTiming.HEARTBEAT_TTL;

import java.util.Map;

import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.domain.model.ServiceInstanceInfo;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import com.ronan.serialx.infra.support.RedisSnapshotCodec;
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
        setHashValue(key, RedisSnapshotCodec.toHash(instanceInfo));
        refreshHeartbeat(instanceInfo.getInstanceId(), instanceInfo.getRuntimeId());
    }

    /**
     * 写入节点离线快照。
     */
    public void saveOffline(ServiceInstanceInfo instanceInfo) {
        String key = String.format(ServiceInstanceRedisConstants.INSTANCE_KEY_FORMAT, instanceInfo.getInstanceId());
        setHashValue(key, RedisSnapshotCodec.toHash(instanceInfo));
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
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(heartbeatKey, runtimeId, HEARTBEAT_TTL));
    }

    /**
     * 写入 Namespace 加载状态。
     */
    public void saveOrUpdate(ServiceInstanceConfigStatusDO status) {
        String namespaceCode = StringUtils.hasText(status.getNamespaceCode()) ? status.getNamespaceCode() : ServiceInstanceRedisConstants.NODE_NAMESPACE;
        String key = String.format(ServiceInstanceRedisConstants.INSTANCE_NAMESPACE_KEY_FORMAT, status.getInstanceId(), namespaceCode);
        setHashValue(key, RedisSnapshotCodec.toHash(status));
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
        stringRedisTemplate.opsForValue().set(heartbeatKey, runtimeId, HEARTBEAT_TTL);
    }

    private void removeHeartbeat(String instanceId) {
        String heartbeatKey = String.format(
                ServiceInstanceRedisConstants.INSTANCE_HEARTBEAT_KEY_FORMAT,
                instanceId);
        stringRedisTemplate.delete(heartbeatKey);
    }

}
