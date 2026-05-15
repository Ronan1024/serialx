package com.ronan.serialx.config;

import java.time.Duration;
import java.util.UUID;

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
@ConfigurationProperties(prefix = "serialx.service.namespace.refresh")
public class NamespaceRefreshProperties {

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
     * 是否启用 Namespace 动态刷新。
     */
    private boolean enabled = true;
}
