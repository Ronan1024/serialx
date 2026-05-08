package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * Namespace 变更日志响应对象。
 */
@Getter
@Builder
public class NamespaceChangeLogResponse {

    /**
     * 主键 ID。
     */
    private Long id;

    /**
     * 版本号。
     */
    private Integer version;

    /**
     * 动作类型编码。
     */
    private Integer actionType;

    /**
     * 动作类型名称。
     */
    private String actionTypeName;

    /**
     * 变更前内容。
     */
    private JsonNode beforeJson;

    /**
     * 变更后内容。
     */
    private JsonNode afterJson;

    /**
     * 操作人。
     */
    private String operator;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
