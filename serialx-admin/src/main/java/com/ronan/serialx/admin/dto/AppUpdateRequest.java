package com.ronan.serialx.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新业务调用方应用请求。
 */
@Getter
@Setter
public class AppUpdateRequest {

    /**
     * 应用名称。
     */
    @Size(max = 128, message = "应用名称长度不能超过 128")
    private String appName;

    /**
     * 应用状态，对应 AppStatusEnum。
     */
    private Integer status;

    /**
     * 负责人。
     */
    @Size(max = 64, message = "负责人长度不能超过 64")
    private String owner;

    /**
     * 备注说明。
     */
    @Size(max = 512, message = "备注长度不能超过 512")
    private String remark;
}
