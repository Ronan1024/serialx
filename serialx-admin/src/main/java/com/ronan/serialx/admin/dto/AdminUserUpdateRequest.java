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
     * 用户状态。
     */
    @Min(0)
    @Max(1)
    private Integer status;

    /**
     * 用户角色。
     */
    @Size(max = 128)
    private String roles;
}
