package com.ronan.serialx.coordinator.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 协调连接预算独立于后台连接池；连接的主库地址及凭证仍来自 spring.datasource。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "serialx.coordinator.persistence")
public class CoordinatorPersistenceProperties {
    /** 协调事务最多占用的连接数；后台查询不会占用此池。 */
    private int maximumPoolSize = 8;
    /** 保留的最小空闲连接数。 */
    private int minimumIdle = 1;
    /** 池内连接耗尽后的等待上限；超过后由协议层返回可重试错误。 */
    private Duration connectionTimeout = Duration.ofSeconds(1);

    /** Hikari 的连接等待不得小于 250 毫秒，连接预算必须自洽。 */
    @PostConstruct
    public void validate() {
        if (maximumPoolSize < 1 || minimumIdle < 0 || minimumIdle > maximumPoolSize
                || connectionTimeout == null || connectionTimeout.toMillis() < 250) {
            throw new IllegalArgumentException("协调连接池容量或连接等待配置不合法");
        }
    }
}
