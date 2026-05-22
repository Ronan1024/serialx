package com.ronan.serialx.application.stats;

import lombok.Builder;
import lombok.Getter;

/**
 * 单次发号调用统计记录。
 */
@Getter
@Builder
public class CallStatsRecord {

    private final String namespaceCode;

    private final String clientName;

    private final String clientId;

    private final String operation;

    private final boolean success;

    private final String errorCode;

    private final int generatedCount;

    private final long latencyMs;
}
