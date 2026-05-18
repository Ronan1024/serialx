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
    @NotBlank(message = "Namespace 名称不能为空")
    @Size(max = 128, message = "Namespace 名称长度不能超过 128")
    private String namespaceName;

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
}
