package com.ronan.serialx.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.Map;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.connection.DataType;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Service 实例状态 Redis 查询测试。
 */
class ServiceInstanceConfigStatusServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldReadNewAndLegacyHashTimesAsTypedObjects() {
        var redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.keys("serialx:service:instance:*"))
                .thenReturn(Set.of("serialx:service:instance:svc-1"));
        when(redis.keys("serialx:service:instance-namespace:*"))
                .thenReturn(Set.of("serialx:service:instance-namespace:svc-1:order_id"));
        when(hashes.entries("serialx:service:instance:svc-1")).thenReturn(Map.of(
                "instanceId", "svc-1", "runtimeId", "runtime-1", "ip", "127.0.0.1", "port", "8081",
                "status", "ACTIVE", "lastReportTime", "2026-10-13 10:20:30.123"));
        when(hashes.entries("serialx:service:instance-namespace:svc-1:order_id")).thenReturn(Map.of(
                "instanceId", "svc-1", "namespaceCode", "order_id", "loadedVersion", "3", "runtimeStatus", "LOADED",
                "lastReportTime", "2026-10-13T10:20:30.123456789"));
        when(redis.hasKey("serialx:service:instance-heartbeat:svc-1")).thenReturn(true);
        var service = new ServiceInstanceConfigStatusServiceImpl(redis, new ObjectMapper());
        var all = service.listAll();
        assertEquals(2, all.size());
        var node = all.stream().filter(row -> row.getNamespaceCode() == null).findFirst().orElseThrow();
        assertEquals("serialx-service/127.0.0.1:8081", node.getHost());
        assertEquals("ACTIVE", node.getRuntimeStatus());
        assertEquals(123000000, node.getLastReportTime().getNano());
        var namespace = service.listByNamespaceCode("order_id").getFirst();
        assertEquals(3, namespace.getLoadedVersion());
        assertEquals(123456789, namespace.getLastReportTime().getNano());
        assertEquals("LOADED", namespace.getRuntimeStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReadLegacyStringKeysWithoutHashWrongTypeErrors() {
        var redis = mock(StringRedisTemplate.class);
        var hashes = mock(HashOperations.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForValue()).thenReturn(values);
        String key = "serialx:service:instance:svc-1";
        when(redis.keys("serialx:service:instance:*")).thenReturn(Set.of(key));
        when(redis.type(key)).thenReturn(DataType.STRING);
        when(values.get(key)).thenReturn("{\"instanceId\":\"svc-1\",\"status\":\"ACTIVE\",\"futureField\":\"ignored\",\"lastReportTime\":\"2026-10-13T10:20:30\"}");
        when(redis.hasKey("serialx:service:instance-heartbeat:svc-1")).thenReturn(true);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var all = new ServiceInstanceConfigStatusServiceImpl(redis, mapper).listAll();
        assertEquals(1, all.size());
        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 20, 30), all.getFirst().getLastReportTime());
        verifyNoInteractions(hashes);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReadStatusesFromRedis() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.keys("serialx:service:instance-status:*"))
                .thenReturn(Set.of("serialx:service:instance-status:svc-1"));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId("svc-1");
        status.setNamespaceCode("order_id");
        status.setHost("serialx-host/127.0.0.1");
        status.setLoadedVersion(2);
        status.setRuntimeStatus("ACTIVE");
        status.setLastReportTime(LocalDateTime.now());
        when(valueOperations.get("serialx:service:instance-status:svc-1"))
                .thenReturn(objectMapper.writeValueAsString(status));
        ServiceInstanceConfigStatusServiceImpl service =
                new ServiceInstanceConfigStatusServiceImpl(redisTemplate, objectMapper);

        var statuses = service.listByNamespaceCode("order_id");

        assertEquals(1, statuses.size());
        assertEquals("svc-1", statuses.get(0).getInstanceId());
        assertEquals(2, statuses.get(0).getLoadedVersion());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReadAllStatusesFromRedis() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.keys("serialx:service:instance-status:*"))
                .thenReturn(Set.of("serialx:service:instance-status:svc-1"));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId("svc-1");
        status.setNamespaceCode("order_id");
        status.setHost("serialx-host/127.0.0.1");
        status.setLoadedVersion(2);
        status.setRuntimeStatus("ACTIVE");
        status.setLastReportTime(LocalDateTime.now());
        when(valueOperations.get("serialx:service:instance-status:svc-1"))
                .thenReturn(objectMapper.writeValueAsString(status));
        ServiceInstanceConfigStatusServiceImpl service =
                new ServiceInstanceConfigStatusServiceImpl(redisTemplate, objectMapper);

        var statuses = service.listAll();

        assertEquals(1, statuses.size());
        assertEquals("order_id", statuses.get(0).getNamespaceCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMarkStatusOfflineWhenHeartbeatExpired() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.keys("serialx:service:instance-status:*"))
                .thenReturn(Set.of("serialx:service:instance-status:svc-1"));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId("svc-1");
        status.setNamespaceCode("order_id");
        status.setHost("serialx-host/127.0.0.1");
        status.setLoadedVersion(2);
        status.setRuntimeStatus("ACTIVE");
        status.setLastReportTime(LocalDateTime.now().minusSeconds(120));
        when(valueOperations.get("serialx:service:instance-status:svc-1"))
                .thenReturn(objectMapper.writeValueAsString(status));
        ServiceInstanceConfigStatusServiceImpl service =
                new ServiceInstanceConfigStatusServiceImpl(redisTemplate, objectMapper);

        var statuses = service.listByNamespaceCode("order_id");

        assertEquals("OFFLINE", statuses.get(0).getRuntimeStatus());
        assertNotNull(statuses.get(0).getLastOfflineTime());
    }
}
