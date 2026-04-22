package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * 后台用户响应。
 */
@Getter
@Builder
public class AdminUserResponse {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 登录用户名。
     */
    private String username;

    /**
     * 用户展示名称。
     */
    private String displayName;

    /**
     * 邮箱地址。
     */
    private String email;

    /**
     * 手机号码。
     */
    private String mobile;

    /**
     * 用户状态。
     */
    private Integer status;

    /**
     * 用户角色。
     */
    private String roles;

    /**
     * 最后登录时间。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
