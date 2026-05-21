package com.ronan.serialx.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Namespace 刷新配置属性。
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "serialx.service.instance")
public class ServiceInstanceProperties {

    /**
     * Redis 通知频道。
     */
    private String channel = "serialx:namespace:change";

    /**
     * 全量对齐间隔。
     */
    private Duration fullSyncInterval = Duration.ofSeconds(30);

    /**
     * 实例状态上报间隔。
     */
    private Duration reportInterval = Duration.ofSeconds(20);

    /**
     * 实例状态快照 Redis 保留时间。
     */
    private Duration statusTtl = Duration.ofHours(24);

    /**
     * 当前实例 ID。
     */
    private String instanceId;

    /**
     * 对外上报 IP。为空时自动探测本机 IPv4 地址。
     */
    private String advertisedIp;

    /**
     * 是否启用实例注册与状态上报。
     */
    private boolean enabled = true;

    /**
     * 是否启用 Namespace 动态刷新。
     */
    private boolean namespaceRefreshEnabled = true;
}
