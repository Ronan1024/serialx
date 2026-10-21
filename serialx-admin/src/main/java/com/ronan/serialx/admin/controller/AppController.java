package com.ronan.serialx.admin.controller;

import com.ronan.serialx.admin.dto.AppCreateRequest;
import com.ronan.serialx.admin.dto.AppPageQueryRequest;
import com.ronan.serialx.admin.dto.AppResponse;
import com.ronan.serialx.admin.dto.AppSecretResetRequest;
import com.ronan.serialx.admin.dto.AppUpdateRequest;
import com.ronan.serialx.admin.manager.AppManager;
import com.ronan.serialx.common.response.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @PostMapping("/page")
    public PageResult<AppResponse> pageApps(@RequestBody AppPageQueryRequest request) {
        return appManager.pageApps(
                request.getPageNo(), request.getPageSize(), request.getKeyword(), request.getStatus());
    }

    /**
     * 查询应用详情。
     */
    @GetMapping("/{id}")
    public AppResponse getApp(@PathVariable Long id) {
        return appManager.getApp(id);
    }

    /**
     * 创建应用。
     */
    @PostMapping
    public AppResponse createApp(@Valid @RequestBody AppCreateRequest request) {
        return appManager.createApp(request);
    }

    /**
     * 更新应用基础信息。
     */
    @PutMapping("/{id}")
    public AppResponse updateApp(
            @PathVariable Long id, @Valid @RequestBody AppUpdateRequest request) {
        return appManager.updateApp(id, request);
    }

    /**
     * 重置应用密钥。
     */
    @PutMapping("/{id}/secret")
    public void resetSecret(
            @PathVariable Long id, @Valid @RequestBody AppSecretResetRequest request) {
        appManager.resetSecret(id, request);
    }
}
