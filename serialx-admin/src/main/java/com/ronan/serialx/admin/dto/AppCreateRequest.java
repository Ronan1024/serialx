package com.ronan.serialx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建业务调用方应用请求。
 */
@Getter
@Setter
public class AppCreateRequest {

    /**
     * 业务调用方 AppKey。
     */
    @NotBlank(message = "AppKey 不能为空")
    @Size(min = 3, max = 64, message = "AppKey 长度必须在 3 到 64 之间")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{2,63}$", message = "AppKey 必须以字母开头，只能包含字母、数字、下划线和横线")
    private String appKey;

    /**
     * 应用名称。
     */
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 128, message = "应用名称长度不能超过 128")
    private String appName;

    /**
     * AppSecret 明文，仅用于创建时生成摘要，不会返回或持久化明文。
     */
    @NotBlank(message = "AppSecret 不能为空")
    @Size(min = 16, max = 128, message = "AppSecret 长度必须在 16 到 128 之间")
    private String appSecret;

    /**
     * 负责人。
     */
    @NotBlank(message = "负责人不能为空")
    @Size(max = 64, message = "负责人长度不能超过 64")
    private String owner;

    /**
     * 备注说明。
     */
    @Size(max = 512, message = "备注长度不能超过 512")
    private String remark;
}
