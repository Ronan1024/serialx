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
    @NotBlank(message = "namespace code is required")
    @Size(max = 64, message = "namespace code length must be less than or equal to 64")
    private String namespaceCode;

    /**
     * Namespace 名称。
     */
    @NotBlank(message = "namespace name is required")
    @Size(max = 128 , message = "namespace name length must be less than or equal to 128")
    private String namespaceName;

    /**
     * 发号模式。
     */
    @NotNull(message = "id mode is required")
    private Integer idMode;

    /**
     * 负责人。
     */
    @NotBlank(message = "owner is required")
    @Size(max = 64, message = "owner length must be less than or equal to 64")
    private String owner;

    /**
     * 备注说明。
     */
    @Size(max = 512 , message = "remark length must be less than or equal to 512")
    private String remark;

    /**
     * 初始草稿配置。
     */
    private JsonNode config;
}
