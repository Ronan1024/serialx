package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * Service 节点加载 Namespace 响应对象。
 */
@Getter
@Builder
public class ServiceInstanceNodeNamespaceResponse {

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * Namespace 名称。
     */
    private String namespaceName;

    /**
     * 已加载版本。
     */
    private Integer loadedVersion;

    /**
     * 当前发布版本。
     */
    private Integer currentVersion;

    /**
     * 运行状态。
     */
    private String runtimeStatus;

    /**
     * 是否版本落后。
     */
    private boolean lagging;

    /**
     * 最后上报时间。
     */
    private LocalDateTime lastReportTime;

    /**
     * 最近判定离线时间。
     */
    private LocalDateTime lastOfflineTime;
}
