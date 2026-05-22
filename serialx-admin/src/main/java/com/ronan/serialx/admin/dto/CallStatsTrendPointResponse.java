package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * 调用统计趋势点。
 */
@Getter
@Builder
public class CallStatsTrendPointResponse {

    private final LocalDateTime time;

    private final long totalRequests;

    private final long successRequests;

    private final long failedRequests;

    private final double avgLatencyMs;
}
