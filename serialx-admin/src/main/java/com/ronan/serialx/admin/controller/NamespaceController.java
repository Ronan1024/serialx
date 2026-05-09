package com.ronan.serialx.admin.controller;

import java.util.List;

import com.ronan.serialx.admin.dto.NamespaceChangeLogResponse;
import com.ronan.serialx.admin.dto.NamespaceCreateRequest;
import com.ronan.serialx.admin.dto.NamespaceDraftConfigRequest;
import com.ronan.serialx.admin.dto.NamespaceResponse;
import com.ronan.serialx.admin.dto.NamespaceUpdateRequest;
import com.ronan.serialx.admin.dto.ServiceInstanceConfigStatusResponse;
import com.ronan.serialx.admin.manager.NamespaceManager;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.common.response.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Namespace 管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/namespaces")
public class NamespaceController {

    /**
     * Namespace 业务编排。
     */
    private final NamespaceManager namespaceManager;

    /**
     * 分页查询 Namespace。
     */
    @GetMapping
    public ApiResponse<PageResult<NamespaceResponse>> pageNamespaces(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer idMode) {
        return ApiResponse.success(namespaceManager.pageNamespaces(pageNo, pageSize, keyword, status, idMode));
    }

    /**
     * 查询 Namespace 详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<NamespaceResponse> getNamespace(@PathVariable Long id) {
        return ApiResponse.success(namespaceManager.getNamespace(id));
    }

    /**
     * 创建 Namespace。
     */
    @PostMapping
    public ApiResponse<NamespaceResponse> createNamespace(@Validated @RequestBody NamespaceCreateRequest request) {
        return ApiResponse.success(namespaceManager.createNamespace(request));
    }

    /**
     * 更新 Namespace 基础信息。
     */
    @PutMapping("/{id}")
    public ApiResponse<NamespaceResponse> updateNamespace(@PathVariable Long id, @Validated @RequestBody NamespaceUpdateRequest request) {
        return ApiResponse.success(namespaceManager.updateNamespace(id, request));
    }

    /**
     * 保存草稿配置。
     */
    @PutMapping("/{id}/config")
    public ApiResponse<NamespaceResponse> saveDraftConfig(@PathVariable Long id, @Valid @RequestBody NamespaceDraftConfigRequest request) {
        return ApiResponse.success(namespaceManager.saveDraftConfig(id, request));
    }

    /**
     * 发布 Namespace。
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<NamespaceResponse> publishNamespace(@PathVariable Long id) {
        return ApiResponse.success(namespaceManager.publishNamespace(id));
    }

    /**
     * 启用 Namespace。
     */
    @PostMapping("/{id}/enable")
    public ApiResponse<NamespaceResponse> enableNamespace(@PathVariable Long id) {
        return ApiResponse.success(namespaceManager.enableNamespace(id));
    }

    /**
     * 停用 Namespace。
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<NamespaceResponse> disableNamespace(@PathVariable Long id) {
        return ApiResponse.success(namespaceManager.disableNamespace(id));
    }

    /**
     * 回滚 Namespace 版本。
     */
    @PostMapping("/{id}/rollback/{version}")
    public ApiResponse<NamespaceResponse> rollbackNamespace(@PathVariable Long id, @PathVariable Integer version) {
        return ApiResponse.success(namespaceManager.rollbackNamespace(id, version));
    }

    /**
     * 删除 Namespace。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNamespace(@PathVariable Long id) {
        namespaceManager.deleteNamespace(id);
        return ApiResponse.success();
    }

    /**
     * 查询 Namespace 变更日志。
     */
    @GetMapping("/{id}/logs")
    public ApiResponse<List<NamespaceChangeLogResponse>> listChangeLogs(@PathVariable Long id) {
        return ApiResponse.success(namespaceManager.listChangeLogs(id));
    }

    /**
     * 查询 Namespace 实例配置状态。
     */
    @GetMapping("/{id}/instances")
    public ApiResponse<List<ServiceInstanceConfigStatusResponse>> listInstanceStatuses(@PathVariable Long id) {
        return ApiResponse.success(namespaceManager.listInstanceStatuses(id));
    }
}
