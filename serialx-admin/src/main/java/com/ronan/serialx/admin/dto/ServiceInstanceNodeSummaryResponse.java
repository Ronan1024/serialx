package com.ronan.serialx.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Service 节点汇总响应对象。
 */
@Getter
@Builder
public class ServiceInstanceNodeSummaryResponse {

    /**
     * 节点总数。
     */
    private long totalCount;

    /**
     * 在线节点数。
     */
    private long onlineCount;

    /**
     * 离线节点数。
     */
    private long offlineCount;

    /**
     * 版本落后的 Namespace 数。
     */
    private long laggingNamespaceCount;
}
