package com.ronan.serialx.config;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 发号调用统计配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "serialx.service.call-stats")
public class CallStatsProperties {

    /**
     * 是否启用调用统计。
     */
    private boolean enabled = true;

    /**
     * 增量统计落库间隔。
     */
    private Duration flushInterval = Duration.ofSeconds(10);

    /**
     * 当前实例业务容量，用于计算负载率。小于等于 0 表示未配置。
     */
    private Long maxQps;
}
