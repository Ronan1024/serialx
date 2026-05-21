package com.ronan.serialx.config;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用签名认证配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "serialx.service.auth.signature")
public class AppSignatureAuthProperties {

    /**
     * 是否启用签名认证。
     */
    private boolean enabled = true;

    /**
     * 签名有效窗口。
     */
    private Duration signatureExpireWindow = Duration.ofMinutes(5);
}
