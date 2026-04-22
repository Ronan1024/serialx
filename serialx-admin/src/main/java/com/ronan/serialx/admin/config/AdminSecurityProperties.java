package com.ronan.serialx.admin.config;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 后台认证安全配置属性。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "serialx.admin.security")
public class AdminSecurityProperties {

    /**
     * JWT 签名密钥。
     */
    private String jwtSecret;

    /**
     * JWT 签发方。
     */
    private String jwtIssuer = "serialx-admin";

    /**
     * 访问令牌有效期。
     */
    private Duration accessTokenTtl = Duration.ofHours(2);

    /**
     * 初始管理员用户名。
     */
    private String bootstrapUsername;

    /**
     * 初始管理员密码。
     */
    private String bootstrapPassword;
}
