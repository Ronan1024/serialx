package com.ronan.serialx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改后台用户密码请求。
 */
@Getter
@Setter
public class AdminUserPasswordRequest {

    /**
     * 新登录密码明文。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度必须在 8 到 128 之间")
    private String password;
}
