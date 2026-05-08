package com.ronan.serialx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新 Namespace 基础信息请求。
 */
@Getter
@Setter
public class NamespaceUpdateRequest {

    /**
     * Namespace 名称。
     */
    @NotBlank
    @Size(max = 128)
    private String namespaceName;

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
}
