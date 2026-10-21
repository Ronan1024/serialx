package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @JsonSerialize(using = ToStringSerializer.class)
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
