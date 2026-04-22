package com.ronan.serialx.admin.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.ronan.serialx.admin.config.AdminSecurityProperties;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.error.SystemErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 后台 JWT 签发和解析组件。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenManager {

    /**
     * 后台安全配置属性。
     */
    private final AdminSecurityProperties properties;

    /**
     * JWT 签名密钥。
     */
    private SecretKey secretKey;

    /**
     * 初始化 JWT 签名密钥。
     */
    @PostConstruct
    void init() {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw BizException.system(SystemErrorCode.CONFIG_ERROR,
                    "serialx.admin.security.jwt-secret must be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为后台用户签发访问令牌。
     */
    public String issueToken(AdminUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());
        return Jwts.builder()
                .issuer(properties.getJwtIssuer())
                .subject(String.valueOf(principal.getUserId()))
                .claim("username", principal.getUsername())
                .claim("roles", principal.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析访问令牌并生成认证主体。
     */
    public AdminUserPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getJwtIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AdminUserPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("roles", String.class));
        } catch (IllegalArgumentException | JwtException ex) {
            throw BizException.unauthorized(
                    BusinessErrorCode.ADMIN_TOKEN_INVALID, BusinessErrorCode.ADMIN_TOKEN_INVALID.getMessage());
        }
    }
}
