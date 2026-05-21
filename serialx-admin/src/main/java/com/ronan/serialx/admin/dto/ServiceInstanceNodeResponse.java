package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Service 节点响应对象。
 */
@Getter
@Builder
public class ServiceInstanceNodeResponse {

    /**
     * 实例 ID。
     */
    private String instanceId;

    /**
     * 服务名称。
     */
    private String serviceName;

    /**
     * 主机标识。
     */
    private String host;

    /**
     * 节点运行状态。
     */
    private String runtimeStatus;

    /**
     * 已加载 Namespace 数。
     */
    private int namespaceCount;

    /**
     * 版本落后的 Namespace 数。
     */
    private int laggingNamespaceCount;

    /**
     * 最后上报时间。
     */
    private LocalDateTime lastReportTime;

    /**
     * 最近判定离线时间。
     */
    private LocalDateTime lastOfflineTime;

    /**
     * 已加载 Namespace 列表。
     */
    private List<ServiceInstanceNodeNamespaceResponse> namespaces;
}
