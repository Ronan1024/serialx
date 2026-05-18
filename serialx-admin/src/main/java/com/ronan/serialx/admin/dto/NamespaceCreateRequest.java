package com.ronan.serialx.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    @NotBlank(message = "Namespace 编码不能为空")
    @Size(max = 64, message = "Namespace 编码长度不能超过 64")
    private String namespaceCode;

    /**
     * Namespace 名称。
     */
    @NotBlank(message = "Namespace 名称不能为空")
    @Size(max = 128, message = "Namespace 名称长度不能超过 128")
    private String namespaceName;

    /**
     * 发号模式。
     */
    @NotNull(message = "发号模式不能为空")
    private Integer idMode;

    /**
     * 负责人。
     */
    @NotBlank(message = "负责人不能为空")
    @Size(max = 64, message = "负责人长度不能超过 64")
    private String owner;

    /**
     * 备注说明。
     */
    @Size(max = 512, message = "备注长度不能超过 512")
    private String remark;

    /**
     * 初始草稿配置。
     */
    @JsonAlias("draftConfig")
    private JsonNode config;
}
