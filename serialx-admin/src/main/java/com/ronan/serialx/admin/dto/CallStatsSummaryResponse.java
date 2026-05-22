package com.ronan.serialx.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 调用统计集群汇总。
 */
@Getter
@Builder
public class CallStatsSummaryResponse {

    private final long totalRequests;

    private final long successRequests;

    private final long failedRequests;

    private final long totalIds;

    private final double avgQps;

    private final double avgLatencyMs;

    private final long maxLatencyMs;

    private final double errorRate;

    private final long onlineInstanceCount;

    private final double avgLoadRate;
}
