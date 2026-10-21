package com.ronan.serialx.adapter.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.domain.model.ServiceInstanceInfo;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Service 实例状态 Redis 写入测试。
 */
class ServiceInstanceStatusWriterImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void legacyReportIntervalCannotExtendHeartbeatTtl() {
        var properties = new ServiceInstanceProperties();
        var binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "serialx.service.instance.report-interval", "1d",
                "serialx.service.instance.status-ttl", "1s")));
        binder.bind("serialx.service.instance", Bindable.ofInstance(properties));
        var redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.setIfAbsent("serialx:service:instance-heartbeat:svc-1", "runtime-1",
                Duration.ofSeconds(60))).thenReturn(true);
        var writer = new ServiceInstanceStatusWriterImpl(redisTemplate, properties);

        assertTrue(writer.tryAcquireHeartbeat("svc-1", "runtime-1"));
        var instance = new ServiceInstanceInfo();
        instance.setInstanceId("svc-1");
        instance.setRuntimeId("runtime-1");
        writer.saveOnline(instance);

        verify(valueOperations).set("serialx:service:instance-heartbeat:svc-1", "runtime-1", Duration.ofSeconds(60));
        verify(redisTemplate).expire("serialx:service:instance:svc-1", Duration.ofSeconds(1));
        assertFalse(new BeanWrapperImpl(properties).isWritableProperty("reportInterval"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteStatusToRedisWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ServiceInstanceProperties properties = new ServiceInstanceProperties();
        properties.setStatusTtl(Duration.ofSeconds(60));
        ServiceInstanceStatusWriterImpl writer = new ServiceInstanceStatusWriterImpl(redisTemplate, properties);
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId("svc-1");
        status.setNamespaceCode("order_id");
        status.setHost("serialx-host/127.0.0.1");
        status.setLoadedVersion(3);
        status.setRuntimeStatus(ServiceInstanceRedisConstants.ACTIVE);
        status.setLastReportTime(LocalDateTime.of(2026, 10, 13, 10, 20, 30, 123456789));

        writer.saveOrUpdate(status);

        verify(hashOperations).putAll(
                eq("serialx:service:instance-namespace:svc-1:order_id"),
                argThat(value -> "order_id".equals(((Map<?, ?>) value).get("namespaceCode"))
                        && "2026-10-13 10:20:30.123".equals(((Map<?, ?>) value).get("lastReportTime"))
                        && "3".equals(((Map<?, ?>) value).get("loadedVersion"))));
        verify(redisTemplate).expire("serialx:service:instance-namespace:svc-1:order_id", Duration.ofSeconds(60));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteNodeHeartbeatWithDedicatedKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ServiceInstanceProperties properties = new ServiceInstanceProperties();
        properties.setStatusTtl(Duration.ofSeconds(60));
        ServiceInstanceStatusWriterImpl writer = new ServiceInstanceStatusWriterImpl(redisTemplate, properties);
        ServiceInstanceInfo instanceInfo = new ServiceInstanceInfo();
        instanceInfo.setInstanceId("svc-1");
        instanceInfo.setRuntimeId("runtime-1");
        instanceInfo.setIp("127.0.0.1");
        instanceInfo.setPort("8081");
        instanceInfo.setStatus(ServiceInstanceRedisConstants.ACTIVE);
        instanceInfo.setLastReportTime(LocalDateTime.now());

        writer.saveOnline(instanceInfo);

        verify(hashOperations).putAll(
                eq("serialx:service:instance:svc-1"),
                argThat(value -> "svc-1".equals(((Map<?, ?>) value).get("instanceId"))));
        verify(redisTemplate).expire("serialx:service:instance:svc-1", Duration.ofSeconds(60));
        verify(valueOperations).set("serialx:service:instance-heartbeat:svc-1", "runtime-1", Duration.ofSeconds(60));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveHeartbeatWhenNodeOffline() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ServiceInstanceProperties properties = new ServiceInstanceProperties();
        properties.setStatusTtl(Duration.ofSeconds(60));
        ServiceInstanceStatusWriterImpl writer = new ServiceInstanceStatusWriterImpl(redisTemplate, properties);
        ServiceInstanceInfo instanceInfo = new ServiceInstanceInfo();
        instanceInfo.setInstanceId("svc-1");
        instanceInfo.setRuntimeId("runtime-1");
        instanceInfo.setStatus(ServiceInstanceRedisConstants.OFFLINE);
        instanceInfo.setLastReportTime(LocalDateTime.now());

        writer.saveOffline(instanceInfo);

        verify(hashOperations).putAll(
                eq("serialx:service:instance:svc-1"),
                argThat(value -> ServiceInstanceRedisConstants.OFFLINE.equals(((Map<?, ?>) value).get("status"))));
        verify(redisTemplate).delete("serialx:service:instance-heartbeat:svc-1");
    }

    @Test
    void shouldAcquireHeartbeatAtomically() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ServiceInstanceProperties properties = new ServiceInstanceProperties();
        ServiceInstanceStatusWriterImpl writer = new ServiceInstanceStatusWriterImpl(redisTemplate, properties);
        when(valueOperations.setIfAbsent(
                "serialx:service:instance-heartbeat:svc-1",
                "runtime-1",
                Duration.ofSeconds(60)))
                .thenReturn(true);

        assertTrue(writer.tryAcquireHeartbeat("svc-1", "runtime-1"));
    }

    @Test
    void shouldRejectDuplicateHeartbeatAcquire() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ServiceInstanceProperties properties = new ServiceInstanceProperties();
        ServiceInstanceStatusWriterImpl writer = new ServiceInstanceStatusWriterImpl(redisTemplate, properties);
        when(valueOperations.setIfAbsent(
                "serialx:service:instance-heartbeat:svc-1",
                "runtime-2",
                Duration.ofSeconds(60)))
                .thenReturn(false);

        assertFalse(writer.tryAcquireHeartbeat("svc-1", "runtime-2"));
    }
}
