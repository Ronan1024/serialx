package com.ronan.serialx.admin.dto;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * 调用统计查询请求。
 */
@Getter
@Setter
public class CallStatsQueryRequest {

    private Instant startTime;

    private Instant endTime;

    private String namespaceCode;

    private String instanceId;

    private String clientId;

    private String clientName;
}
