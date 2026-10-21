package com.ronan.serialx.admin.controller;

import java.util.List;

import com.ronan.serialx.admin.dto.NamespaceChangeLogResponse;
import com.ronan.serialx.admin.dto.NamespaceCreateRequest;
import com.ronan.serialx.admin.dto.NamespaceDraftConfigRequest;
import com.ronan.serialx.admin.dto.NamespacePageQueryRequest;
import com.ronan.serialx.admin.dto.NamespaceResponse;
import com.ronan.serialx.admin.dto.NamespaceUpdateRequest;
import com.ronan.serialx.admin.dto.NamespaceVersionResponse;
import com.ronan.serialx.admin.dto.ServiceInstanceConfigStatusResponse;
import com.ronan.serialx.admin.manager.NamespaceManager;
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
     * 接收管理列表的分页与筛选条件，返回控制面 Namespace 数据。
     *
     * <p>请求体绑定后执行 DTO 声明的 Bean Validation 约束；筛选组合与配置装配交由业务层处理。
     *
     * @param request 分页参数及可选筛选条件
     * @return 筛选后的总记录数、分页信息与当前页数据，不含实例同步状态
     */
    @PostMapping("/page")
    public PageResult<NamespaceResponse> pageNamespaces(@Validated @RequestBody NamespacePageQueryRequest request) {
        return namespaceManager.pageNamespaces(request);
    }

    /**
     * 查询 Namespace 详情。
     */
    @GetMapping("/{id}")
    public NamespaceResponse getNamespace(@PathVariable Long id) {
        return namespaceManager.getNamespace(id);
    }

    /**
     * 创建 Namespace。
     */
    @PostMapping
    public NamespaceResponse createNamespace(@Validated @RequestBody NamespaceCreateRequest request) {
        return namespaceManager.createNamespace(request);
    }

    /**
     * 更新 Namespace 基础信息。
     */
    @PutMapping("/{id}")
    public NamespaceResponse updateNamespace(@PathVariable Long id, @Validated @RequestBody NamespaceUpdateRequest request) {
        return namespaceManager.updateNamespace(id, request);
    }

    /**
     * 保存草稿配置。
     */
    @PutMapping("/{id}/config")
    public NamespaceResponse saveDraftConfig(@PathVariable Long id, @Valid @RequestBody NamespaceDraftConfigRequest request) {
        return namespaceManager.saveDraftConfig(id, request);
    }

    /**
     * 发布 Namespace。
     */
    @PostMapping("/{id}/publish")
    public NamespaceResponse publishNamespace(@PathVariable Long id) {
        return namespaceManager.publishNamespace(id);
    }

    /**
     * 启用 Namespace。
     */
    @PostMapping("/{id}/enable")
    public NamespaceResponse enableNamespace(@PathVariable Long id) {
        return namespaceManager.enableNamespace(id);
    }

    /**
     * 停用 Namespace。
     */
    @PostMapping("/{id}/disable")
    public NamespaceResponse disableNamespace(@PathVariable Long id) {
        return namespaceManager.disableNamespace(id);
    }

    /**
     * 回滚 Namespace 版本。
     */
    @PostMapping("/{id}/rollback/{version}")
    public NamespaceResponse rollbackNamespace(@PathVariable Long id, @PathVariable Integer version) {
        return namespaceManager.rollbackNamespace(id, version);
    }

    /**
     * 删除 Namespace。
     */
    @DeleteMapping("/{id}")
    public void deleteNamespace(@PathVariable Long id) {
        namespaceManager.deleteNamespace(id);
    }

    /**
     * 查询 Namespace 变更日志。
     */
    @GetMapping("/{id}/logs")
    public List<NamespaceChangeLogResponse> listChangeLogs(@PathVariable Long id) {
        return namespaceManager.listChangeLogs(id);
    }

    /**
     * 查询 Namespace 已发布版本。
     */
    @GetMapping("/{id}/versions")
    public List<NamespaceVersionResponse> listPublishedVersions(@PathVariable Long id) {
        return namespaceManager.listPublishedVersions(id);
    }

    /**
     * 查询 Namespace 实例配置状态。
     */
    @GetMapping("/{id}/instances")
    public List<ServiceInstanceConfigStatusResponse> listInstanceStatuses(@PathVariable Long id) {
        return namespaceManager.listInstanceStatuses(id);
    }
}
