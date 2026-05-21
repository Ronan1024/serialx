package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * Namespace 已发布版本响应对象。
 */
@Getter
@Builder
public class NamespaceVersionResponse {

    /**
     * 版本号。
     */
    private Integer version;

    /**
     * 发号模式编码。
     */
    private Integer idMode;

    /**
     * 发号模式名称。
     */
    private String idModeName;

    /**
     * 配置内容。
     */
    private JsonNode config;

    /**
     * 配置校验码。
     */
    private String configChecksum;

    /**
     * 发布人。
     */
    private String operator;

    /**
     * 发布时间。
     */
    private LocalDateTime publishedAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
