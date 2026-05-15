package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * Namespace 响应对象。
 */
@Getter
@Builder
public class NamespaceResponse {

    /**
     * 主键 ID。
     */
    private Long id;

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * Namespace 名称。
     */
    private String namespaceName;

    /**
     * 发号模式编码。
     */
    private Integer idMode;

    /**
     * 发号模式名称。
     */
    private String idModeName;

    /**
     * Namespace 状态编码。
     */
    private Integer status;

    /**
     * Namespace 状态名称。
     */
    private String statusName;

    /**
     * 负责人。
     */
    private String owner;

    /**
     * 备注说明。
     */
    private String remark;

    /**
     * 当前生效版本。
     */
    private Integer currentVersion;

    /**
     * 当前草稿配置。
     */
    private JsonNode draftConfig;

    /**
     * 当前生效配置。
     */
    private JsonNode publishedConfig;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
