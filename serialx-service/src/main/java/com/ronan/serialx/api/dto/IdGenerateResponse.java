package com.ronan.serialx.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 单个 ID 生成响应。
 */
@Getter
@Builder
public class IdGenerateResponse {

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * 已加载版本。
     */
    private Integer version;

    /**
     * ID 字符串。
     */
    private String id;
}
