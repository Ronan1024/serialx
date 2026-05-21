package com.ronan.serialx.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量 ID 生成请求。
 */
@Getter
@Setter
public class IdBatchGenerateRequest {

    /**
     * Namespace 编码。
     */
    @NotBlank(message = "namespaceCode 不能为空")
    private String namespaceCode;

    /**
     * 生成数量。
     */
    @Min(value = 1, message = "count 最小为 1")
    @Max(value = 1000, message = "count 最大为 1000")
    private int count;
}
