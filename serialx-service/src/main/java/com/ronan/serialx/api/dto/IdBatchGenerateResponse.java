package com.ronan.serialx.api.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 批量 ID 生成响应。
 */
@Getter
@Builder
public class IdBatchGenerateResponse {

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * 已加载版本。
     */
    private Integer version;

    /**
     * ID 列表。
     */
    private List<String> ids;
}
