package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * Service 实例配置状态响应对象。
 */
@Getter
@Builder
public class ServiceInstanceConfigStatusResponse {

    /**
     * 实例 ID。
     */
    private String instanceId;

    /**
     * 主机标识。
     */
    private String host;

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * 已加载版本号。
     */
    private Integer loadedVersion;

    /**
     * 运行时状态。
     */
    private String runtimeStatus;

    /**
     * 最后上报时间。
     */
    private LocalDateTime lastReportTime;

    /**
     * 最近判定离线时间。
     */
    private LocalDateTime lastOfflineTime;
}
