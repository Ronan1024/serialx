package com.ronan.serialx.infra.http;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 同一服务进程的 HTTP 出口统一使用此超时配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "serialx.outbound.http")
public class OutboundHttpProperties {
    private static final Duration MIN_TIMEOUT = Duration.ofMillis(1);
    private static final Duration MAX_TIMEOUT = Duration.ofHours(1);

    /**
     * 建立新连接的最大等待时间。
     */
    private Duration connectTimeout = Duration.ofSeconds(1);
    /**
     * 单次请求到完整响应正文接收完成的最大等待时间。
     */
    private Duration requestTimeout = Duration.ofSeconds(2);

    @PostConstruct
    public void validate() {
        validateTimeout("connect-timeout", connectTimeout);
        validateTimeout("request-timeout", requestTimeout);
        if (connectTimeout.compareTo(requestTimeout) > 0) {
            throw new IllegalArgumentException("serialx.outbound.http.connect-timeout 不能大于 request-timeout");
        }
    }

    private void validateTimeout(String name, Duration value) {
        if (value == null || value.compareTo(MIN_TIMEOUT) < 0 || value.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("serialx.outbound.http." + name + " 必须在 1 毫秒至 1 小时之间");
        }
    }
}
