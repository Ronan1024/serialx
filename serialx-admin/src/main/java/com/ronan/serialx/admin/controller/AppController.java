package com.ronan.serialx.admin.controller;

import com.ronan.serialx.admin.dto.AppCreateRequest;
import com.ronan.serialx.admin.dto.AppResponse;
import com.ronan.serialx.admin.dto.AppSecretResetRequest;
import com.ronan.serialx.admin.dto.AppUpdateRequest;
import com.ronan.serialx.admin.manager.AppManager;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.common.response.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务调用方应用管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/apps")
public class AppController {

    /**
     * 应用业务编排。
     */
    private final AppManager appManager;

    /**
     * 分页查询应用。
     */
    @GetMapping
    public ApiResponse<PageResult<AppResponse>> pageApps(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(appManager.pageApps(pageNo, pageSize, keyword, status));
    }

    /**
     * 查询应用详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AppResponse> getApp(@PathVariable Long id) {
        return ApiResponse.success(appManager.getApp(id));
    }

    /**
     * 创建应用。
     */
    @PostMapping
    public ApiResponse<AppResponse> createApp(@Valid @RequestBody AppCreateRequest request) {
        return ApiResponse.success(appManager.createApp(request));
    }

    /**
     * 更新应用基础信息。
     */
    @PutMapping("/{id}")
    public ApiResponse<AppResponse> updateApp(
            @PathVariable Long id, @Valid @RequestBody AppUpdateRequest request) {
        return ApiResponse.success(appManager.updateApp(id, request));
    }

    /**
     * 重置应用密钥。
     */
    @PutMapping("/{id}/secret")
    public ApiResponse<Void> resetSecret(
            @PathVariable Long id, @Valid @RequestBody AppSecretResetRequest request) {
        appManager.resetSecret(id, request);
        return ApiResponse.success();
    }
}
