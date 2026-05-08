package com.ronan.serialx.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建 Namespace 请求。
 */
@Getter
@Setter
public class NamespaceCreateRequest {

    /**
     * Namespace 编码。
     */
    @NotBlank
    @Size(max = 64)
    private String namespaceCode;

    /**
     * Namespace 名称。
     */
    @NotBlank
    @Size(max = 128)
    private String namespaceName;

    /**
     * 发号模式。
     */
    @NotNull
    private Integer idMode;

    /**
     * 负责人。
     */
    @NotBlank
    @Size(max = 64)
    private String owner;

    /**
     * 备注说明。
     */
    @Size(max = 512)
    private String remark;

    /**
     * 初始草稿配置。
     */
    @NotNull
    private JsonNode config;
}
