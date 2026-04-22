package com.ronan.serialx.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建后台用户请求。
 */
@Getter
@Setter
public class AdminUserCreateRequest {

    /**
     * 登录用户名。
     */
    @NotBlank
    @Size(min = 3, max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$")
    private String username;

    /**
     * 登录密码明文。
     */
    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    /**
     * 用户展示名称。
     */
    @Size(max = 64)
    private String displayName;

    /**
     * 邮箱地址。
     */
    @Email
    @Size(max = 128)
    private String email;

    /**
     * 手机号码。
     */
    @Size(max = 32)
    private String mobile;

    /**
     * 用户角色。
     */
    @Size(max = 128)
    private String roles;
}
