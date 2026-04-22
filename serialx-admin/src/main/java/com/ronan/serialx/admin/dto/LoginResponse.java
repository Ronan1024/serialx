package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * 后台登录响应。
 */
@Getter
@Builder
public class LoginResponse {

    /**
     * JWT 访问令牌。
     */
    private String accessToken;

    /**
     * 令牌类型。
     */
    private String tokenType;

    /**
     * 令牌有效秒数。
     */
    private Long expiresInSeconds;

    /**
     * 登录用户信息。
     */
    private AdminUserResponse user;

    /**
     * 登录时间。
     */
    private LocalDateTime loginAt;
}
