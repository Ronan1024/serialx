package com.ronan.serialx.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 发号服务节点健康响应。
 */
@Getter
@Builder
public class IdHealthResponse {

    /**
     * 当前节点是否可提供发号服务。
     */
    private boolean available;

    /**
     * 当前 service 节点 ID。
     */
    private String nodeId;

    /**
     * 当前已加载的 Namespace 数量。
     */
    private int loadedNamespaceCount;

    /**
     * 当前节点时间戳。
     */
    private long timestamp;
}
