package com.ronan.serialx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台登录请求。
 */
@Getter
@Setter
public class LoginRequest {

    /**
     * 登录用户名。
     */
    @NotBlank
    private String username;

    /**
     * 登录密码明文。
     */
    @NotBlank
    private String password;
}
