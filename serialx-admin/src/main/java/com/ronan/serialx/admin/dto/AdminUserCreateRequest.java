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
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须在 3 到 64 之间")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点和横线")
    private String username;

    /**
     * 登录密码明文。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度必须在 8 到 128 之间")
    private String password;

    /**
     * 用户展示名称。
     */
    @Size(max = 64, message = "展示名称长度不能超过 64")
    private String displayName;

    /**
     * 邮箱地址。
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /**
     * 手机号码。
     */
    @Size(max = 32, message = "手机号长度不能超过 32")
    private String mobile;

    /**
     * 用户角色。
     */
    @Size(max = 128, message = "角色长度不能超过 128")
    private String roles;
}
