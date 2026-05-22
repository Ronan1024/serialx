package com.ronan.serialx.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 调用方维度调用统计。
 */
@Getter
@Builder
public class CallStatsClientResponse {

    private final String clientId;

    private final String clientName;

    private final long requestCount;

    private final long successCount;

    private final long failedCount;

    private final long idCount;

    private final double avgQps;

    private final double avgLatencyMs;

    private final long maxLatencyMs;

    private final double errorRate;
}
