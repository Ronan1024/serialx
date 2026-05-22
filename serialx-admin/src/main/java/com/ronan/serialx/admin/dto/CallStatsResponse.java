package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 调用统计页面响应。
 */
@Getter
@Builder
public class CallStatsResponse {

    private final LocalDateTime startTime;

    private final LocalDateTime endTime;

    private final CallStatsSummaryResponse summary;

    private final List<CallStatsTrendPointResponse> trend;

    private final List<CallStatsInstanceResponse> instances;

    private final List<CallStatsNamespaceResponse> namespaces;

    private final List<CallStatsClientResponse> clients;

    private final List<CallStatsErrorResponse> errors;
}
