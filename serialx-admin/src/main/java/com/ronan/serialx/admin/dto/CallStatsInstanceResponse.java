package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * Service 实例调用与负载统计。
 */
@Getter
@Builder
public class CallStatsInstanceResponse {

    private final String instanceId;

    private final String host;

    private final String runtimeStatus;

    private final LocalDateTime lastReportTime;

    private final int namespaceCount;

    private final int laggingNamespaceCount;

    private final long requestCount;

    private final long successCount;

    private final long failedCount;

    private final long idCount;

    private final double currentQps;

    private final double avgLatencyMs;

    private final long maxLatencyMs;

    private final double errorRate;

    private final Long maxQps;

    private final Double loadRate;
}
