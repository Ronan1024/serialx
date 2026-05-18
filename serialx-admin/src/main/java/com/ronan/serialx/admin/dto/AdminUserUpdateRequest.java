package com.ronan.serialx.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新后台用户请求。
 */
@Getter
@Setter
public class AdminUserUpdateRequest {

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
     * 用户状态。
     */
    @Min(value = 0, message = "用户状态不能小于 0")
    @Max(value = 1, message = "用户状态不能大于 1")
    private Integer status;

    /**
     * 用户角色。
     */
    @Size(max = 128, message = "角色长度不能超过 128")
    private String roles;
}
