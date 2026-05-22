package com.ronan.serialx.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 调用失败分布。
 */
@Getter
@Builder
public class CallStatsErrorResponse {

    private final String errorCode;

    private final long count;

    private final double percentage;
}
