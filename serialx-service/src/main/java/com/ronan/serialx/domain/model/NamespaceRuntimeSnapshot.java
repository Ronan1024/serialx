package com.ronan.serialx.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * Namespace 运行时快照。
 */
@Getter
@Builder
public class NamespaceRuntimeSnapshot {

    /**
     * Namespace 主键 ID。
     */
    private Long namespaceId;

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * 配置版本。
     */
    private Integer version;

    /**
     * 发号模式。
     */
    private Integer idMode;

    /**
     * 配置 JSON。
     */
    private JsonNode config;
}
