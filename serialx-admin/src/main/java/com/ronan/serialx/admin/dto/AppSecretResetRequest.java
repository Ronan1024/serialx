package com.ronan.serialx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 重置业务调用方应用密钥请求。
 */
@Getter
@Setter
public class AppSecretResetRequest {

    /**
     * 新 AppSecret 明文，仅用于生成摘要，不会返回或持久化明文。
     */
    @NotBlank(message = "AppSecret 不能为空")
    @Size(min = 16, max = 128, message = "AppSecret 长度必须在 16 到 128 之间")
    private String appSecret;
}
