package com.ronan.serialx.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Namespace 运行时状态响应。
 */
@Getter
@Builder
public class NamespaceRuntimeStatusResponse {

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * 已加载版本。
     */
    private Integer version;

    /**
     * 发号模式。
     */
    private Integer idMode;

    /**
     * 运行状态。
     */
    private String runtimeStatus;
}
