package com.ronan.serialx.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Namespace 维度调用统计。
 */
@Getter
@Builder
public class CallStatsNamespaceResponse {

    private final String namespaceCode;

    private final long requestCount;

    private final long successCount;

    private final long failedCount;

    private final long idCount;

    private final double avgQps;

    private final double avgLatencyMs;

    private final long maxLatencyMs;

    private final double errorRate;

    private final long instanceCount;
}
