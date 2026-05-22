package com.ronan.serialx.admin.controller;

import java.time.Instant;

import com.ronan.serialx.admin.dto.CallStatsQueryRequest;
import com.ronan.serialx.admin.dto.CallStatsResponse;
import com.ronan.serialx.admin.manager.CallStatsManager;
import com.ronan.serialx.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调用统计管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/call-stats")
public class CallStatsController {

    private final CallStatsManager callStatsManager;

    /**
     * 查询调用统计页面数据。
     */
    @GetMapping
    public ApiResponse<CallStatsResponse> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(required = false) String namespaceCode,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String clientName) {
        CallStatsQueryRequest request = new CallStatsQueryRequest();
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setNamespaceCode(normalize(namespaceCode));
        request.setInstanceId(normalize(instanceId));
        request.setClientId(normalize(clientId));
        request.setClientName(normalize(clientName));
        return ApiResponse.success(callStatsManager.query(request));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
