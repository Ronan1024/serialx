package com.ronan.serialx.admin.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service 实例配置状态 Redis 查询服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceInstanceConfigStatusServiceImpl implements ServiceInstanceConfigStatusService {

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
        return listByStatusKeyPattern("serialx:service:instance-status:*:*");
    }

    @Override
    public List<ServiceInstanceConfigStatusDO> listByNamespaceCode(String namespaceCode) {
        return listByStatusKeyPattern("serialx:service:instance-status:" + namespaceCode + ":*");
    }

    private List<ServiceInstanceConfigStatusDO> listByStatusKeyPattern(String keyPattern) {
        Set<String> keys = stringRedisTemplate.keys(keyPattern);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(key -> stringRedisTemplate.opsForValue().get(key))
                .filter(Objects::nonNull)
                .map(this::readStatus)
                .filter(Objects::nonNull)
                .map(this::markOfflineIfNeeded)
                .sorted((left, right) -> {
                    if (left.getLastReportTime() == null || right.getLastReportTime() == null) {
                        return 0;
                    }
                    return right.getLastReportTime().compareTo(left.getLastReportTime());
                })
                .toList();
    }

    private ServiceInstanceConfigStatusDO readStatus(String statusJson) {
        try {
            return objectMapper.readValue(statusJson, ServiceInstanceConfigStatusDO.class);
        } catch (JsonProcessingException ex) {
            log.warn("deserialize service instance status failed, statusJson={}", statusJson, ex);
            return null;
        }
    }

    private ServiceInstanceConfigStatusDO markOfflineIfNeeded(ServiceInstanceConfigStatusDO status) {
        LocalDateTime lastReportTime = status.getLastReportTime();
        if (lastReportTime == null) {
            status.setRuntimeStatus("OFFLINE");
            status.setLastOfflineTime(LocalDateTime.now());
            return status;
        }
        LocalDateTime offlineTime = lastReportTime.plus(aliveTimeout);
        if (offlineTime.isBefore(LocalDateTime.now())) {
            status.setRuntimeStatus("OFFLINE");
            status.setLastOfflineTime(offlineTime);
        }
        return status;
    }
}
