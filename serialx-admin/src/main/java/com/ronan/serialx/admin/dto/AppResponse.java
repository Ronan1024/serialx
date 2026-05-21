package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * 业务调用方应用响应。
 */
@Getter
@Builder
public class AppResponse {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 业务调用方 AppKey。
     */
    private String appKey;

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 应用状态。
     */
    private Integer status;

    /**
     * 负责人。
     */
    private String owner;

    /**
     * 备注说明。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
