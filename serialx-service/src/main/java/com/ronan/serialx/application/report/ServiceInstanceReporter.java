package com.ronan.serialx.application.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.util.Assert;
import com.ronan.serialx.common.util.StringFormat;
import com.ronan.serialx.config.NamespaceRefreshProperties;
import com.ronan.serialx.domain.ServiceInstanceInfo;
import com.ronan.serialx.utils.IpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * @program: serialx
 * @description:
 * @author: L.J.Ran
 * @create: 2026/5/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceInstanceReporter implements SmartLifecycle {

    private final NamespaceRefreshProperties namespaceRefreshProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private volatile boolean running = true;
    private final ThreadPoolTaskScheduler taskScheduler;

    @Value("${server.port}")
    private String port;


    private static final String KEY = "serialx:service:instance-info:{}";

    /**
     * 定时上报实例状态。
     */
    public void scheduledReportStatus() {
        String instanceKey = StringFormat.format(KEY, namespaceRefreshProperties.getInstanceId());
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(instanceKey))) {
            return;
        }
        String format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String value = stringRedisTemplate.opsForValue().get(instanceKey);
        try {
            ServiceInstanceInfo serviceInstanceInfo = objectMapper.readValue(value, ServiceInstanceInfo.class);
            serviceInstanceInfo.setLastReportTime(format);

            stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
                try {
                    connection.stringCommands()
                            .set(instanceKey.getBytes(StandardCharsets.UTF_8), objectMapper.writeValueAsBytes(serviceInstanceInfo),
                                    Expiration.keepTtl(), RedisStringCommands.SetOption.upsert());
                } catch (JsonProcessingException e) {
                    log.warn("deserialize service instance info failed, instanceId={}", namespaceRefreshProperties.getInstanceId(), e);
                }
                return null;
            });
            String instanceActiveKey = instanceKey + ":active";
            stringRedisTemplate.opsForValue().set(instanceActiveKey, format, namespaceRefreshProperties.getReportInterval());
        } catch (JsonProcessingException e) {
            log.warn("deserialize service instance info failed, instanceId={}", namespaceRefreshProperties.getInstanceId(), e);
        }
    }

    /**
     * 注册当前实例
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registeredInstance() throws UnknownHostException, JsonProcessingException {
        Assert.isFalse(!StringUtils.hasText(namespaceRefreshProperties.getInstanceId()), () -> new RuntimeException("instance id already registered"));
        String format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        ServiceInstanceInfo instanceInfo = new ServiceInstanceInfo();
        instanceInfo.setInstanceId(namespaceRefreshProperties.getInstanceId());
        instanceInfo.setHostName(InetAddress.getLocalHost().getHostName());
        instanceInfo.setCreateTime(format);
        instanceInfo.setIp(IpUtil.getLocalIp());
        instanceInfo.setPort(port);
        instanceInfo.setRuntimeId(UUID.randomUUID().toString());
        String instanceKey = StringFormat.format(KEY, namespaceRefreshProperties.getInstanceId());
        String instanceActiveKey = instanceKey + ":active";
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(instanceKey)) &&
                Boolean.TRUE.equals(stringRedisTemplate.hasKey(instanceActiveKey))) {
            throw new RuntimeException("===>>>> instance already registered <<<<===");
        }

        stringRedisTemplate.opsForValue().set(instanceKey, objectMapper.writeValueAsString(instanceInfo), namespaceRefreshProperties.getStatusTtl());

        taskScheduler.scheduleWithFixedDelay(
                this::scheduledReportStatus,
                Duration.ofMillis(namespaceRefreshProperties.getReportInterval().toMillis())
        );
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        String instanceKey = StringFormat.format(KEY, namespaceRefreshProperties.getInstanceId());

        stringRedisTemplate.delete(instanceKey);

        String instanceActiveKey = instanceKey + ":active";
        stringRedisTemplate.delete(instanceActiveKey);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

