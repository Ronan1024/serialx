package com.ronan.serialx.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 单个 ID 生成请求。
 */
@Getter
@Setter
public class IdGenerateRequest {

    /**
     * Namespace 编码。
     */
    @NotBlank(message = "namespaceCode 不能为空")
    private String namespaceCode;
}
