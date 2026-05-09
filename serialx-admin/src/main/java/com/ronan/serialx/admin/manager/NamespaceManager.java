package com.ronan.serialx.admin.manager;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.ronan.serialx.admin.convert.NamespaceConvert;
import com.ronan.serialx.admin.dto.NamespaceChangeLogResponse;
import com.ronan.serialx.admin.dto.NamespaceCreateRequest;
import com.ronan.serialx.admin.dto.NamespaceDraftConfigRequest;
import com.ronan.serialx.admin.dto.NamespaceResponse;
import com.ronan.serialx.admin.dto.NamespaceUpdateRequest;
import com.ronan.serialx.admin.dto.ServiceInstanceConfigStatusResponse;
import com.ronan.serialx.admin.manager.support.config.NamespaceConfigHandlerRegistry;
import com.ronan.serialx.admin.manager.support.NamespaceRequestValidator;
import com.ronan.serialx.admin.manager.support.NamespaceSnapshotSupport;
import com.ronan.serialx.admin.security.AdminSecurityContextUtils;
import com.ronan.serialx.admin.service.NamespaceChangeLogService;
import com.ronan.serialx.admin.service.NamespaceConfigService;
import com.ronan.serialx.admin.service.NamespaceService;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.common.enums.NamespaceChangeActionEnum;
import com.ronan.serialx.common.enums.NamespaceIdModeEnum;
import com.ronan.serialx.common.enums.NamespacePublishStatusEnum;
import com.ronan.serialx.common.enums.NamespaceStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.model.NamespaceChangeMessage;
import com.ronan.serialx.common.response.PageResult;
import com.ronan.serialx.common.util.Assert;
import com.ronan.serialx.infra.entity.NamespaceChangeLogDO;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Namespace 业务编排。
 */
@Component
@RequiredArgsConstructor
public class NamespaceManager {

    /**
     * Namespace 数据访问服务。
     */
    private final NamespaceService namespaceService;

    /**
     * Namespace 配置数据访问服务。
     */
    private final NamespaceConfigService namespaceConfigService;

    /**
     * Namespace 变更日志服务。
     */
    private final NamespaceChangeLogService namespaceChangeLogService;

    /**
     * Service 实例配置状态服务。
     */
    private final ServiceInstanceConfigStatusService serviceInstanceConfigStatusService;

    /**
     * Namespace 变更通知发布器。
     */
    private final NamespaceChangePublisher namespaceChangePublisher;

    /**
     * Namespace 顶层请求字段校验器。
     */
    private final NamespaceRequestValidator namespaceRequestValidator;

    /**
     * Namespace 快照与配置支持组件。
     */
    private final NamespaceSnapshotSupport namespaceSnapshotSupport;

    /**
     * Namespace 配置处理器注册表。
     */
    private final NamespaceConfigHandlerRegistry namespaceConfigHandlerRegistry;

    /**
     * 分页查询 Namespace。
     */
    public PageResult<NamespaceResponse> pageNamespaces(long pageNo, long pageSize, String keyword, Integer status, Integer idMode) {
        namespaceRequestValidator.validateStatus(status);
        namespaceRequestValidator.validateIdMode(idMode);

        long normalizedPageNo = Math.max(pageNo, 1);
        long normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<NamespaceDO> page = namespaceService.pageNamespaces(normalizedPageNo, normalizedPageSize, keyword, status, idMode);
        List<NamespaceResponse> records = page.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 查询 Namespace 详情。
     */
    public NamespaceResponse getNamespace(Long id) {
        return toResponse(requireNamespace(id));
    }

    /**
     * 创建 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse createNamespace(NamespaceCreateRequest request) {
        Assert.isFalse(!ObjectUtils.isEmpty(request.getIdMode()) && !NamespaceIdModeEnum.contains(request.getIdMode()),
                BizException.supplier(BusinessErrorCode.NAMESPACE_CONFIG_INVALID));

        namespaceRequestValidator.validateNamespaceCode(request.getNamespaceCode());

        JsonNode normalizedConfig = namespaceConfigHandlerRegistry.normalize(request.getIdMode(), request.getConfig());

        Assert.isFalse(namespaceService.existsByCode(request.getNamespaceCode().trim()), BusinessErrorCode.NAMESPACE_CODE_EXISTS);

        request.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        NamespaceDO namespace = NamespaceConvert.INSTANCE.toNamespaceDO(request);
        namespaceService.create(namespace);

        NamespaceConfigDO draftConfig = namespaceSnapshotSupport.buildDraftConfig(namespace.getId(), request.getIdMode(), normalizedConfig);
        namespaceConfigService.create(draftConfig);
        createChangeLog(namespace.getId(), 0, NamespaceChangeActionEnum.CREATE, null,
                namespaceSnapshotSupport.snapshot(namespace, draftConfig));
        return toResponse(requireNamespace(namespace.getId()));
    }

    /**
     * 更新 Namespace 基础信息。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse updateNamespace(Long id, NamespaceUpdateRequest request) {
        NamespaceDO namespace = requireNamespace(id);
        String before = namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getEditingConfig(id));
        namespace.setNamespaceName(request.getNamespaceName().trim());
        namespace.setOwner(request.getOwner().trim());
        namespace.setRemark(normalizeRemark(request.getRemark()));
        namespaceService.update(namespace);
        NamespaceDO latest = requireNamespace(id);
        createChangeLog(id, latest.getCurrentVersion(), NamespaceChangeActionEnum.UPDATE, before,
                namespaceSnapshotSupport.snapshot(latest, namespaceConfigService.getEditingConfig(id)));
        return toResponse(latest);
    }

    /**
     * 保存 Namespace 草稿配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse saveDraftConfig(Long id, NamespaceDraftConfigRequest request) {
        NamespaceDO namespace = requireNamespace(id);

        namespaceRequestValidator.validateIdMode(request.getIdMode());

        JsonNode normalizedConfig = namespaceConfigHandlerRegistry.normalize(request.getIdMode(), request.getConfig());

        String before = namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getEditingConfig(id));
        namespace.setIdMode(request.getIdMode());

        namespaceService.update(namespace);
        NamespaceConfigDO editingConfig = namespaceConfigService.getEditingConfig(id);

        if (editingConfig == null) {
            editingConfig = namespaceSnapshotSupport.buildDraftConfig(id, request.getIdMode(), normalizedConfig);
            namespaceConfigService.create(editingConfig);
        } else {
            editingConfig.setIdMode(request.getIdMode());
            editingConfig.setConfigJson(namespaceSnapshotSupport.writeJson(normalizedConfig));
            editingConfig.setConfigChecksum(namespaceSnapshotSupport.calculateChecksum(editingConfig.getConfigJson()));
            namespaceConfigService.update(editingConfig);
        }
        NamespaceDO latest = requireNamespace(id);
        createChangeLog(id, latest.getCurrentVersion(), NamespaceChangeActionEnum.SAVE_DRAFT, before,
                namespaceSnapshotSupport.snapshot(latest, namespaceConfigService.getEditingConfig(id)));
        return toResponse(latest);
    }

    /**
     * 发布 Namespace 配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse publishNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        NamespaceConfigDO editingConfig = namespaceConfigService.getEditingConfig(id);

        Assert.notNull(editingConfig,
                BizException.supplier(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "namespace draft config not found"));

        namespaceConfigHandlerRegistry.normalize(
                editingConfig.getIdMode(), namespaceSnapshotSupport.readJson(editingConfig.getConfigJson()));

        int nextVersion = namespace.getCurrentVersion() + 1;

        String before =
                namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));

        NamespaceConfigDO publishedConfig = NamespaceConvert.INSTANCE.toNamespaceConfigDO(id, nextVersion, editingConfig);
        publishedConfig.setPublishedBy(AdminSecurityContextUtils.currentUsername());
        namespaceConfigService.create(publishedConfig);

        if (NamespaceStatusEnum.DRAFT.getCode().equals(namespace.getStatus())) {
            namespace.setStatus(NamespaceStatusEnum.ENABLED.getCode());
        }

        String snapshot = namespaceSnapshotSupport.snapshot(namespace, publishedConfig);
        namespaceService.update(namespace);

        createChangeLog(id, nextVersion, NamespaceChangeActionEnum.PUBLISH, before, snapshot);

        notifyChange(namespace.getNamespaceCode(), nextVersion, NamespaceChangeActionEnum.PUBLISH);
        return toResponse(requireNamespace(id));
    }

    /**
     * 启用 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse enableNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        Assert.isTrue(
                namespace.getCurrentVersion() > 0,
                BizException.supplier(BusinessErrorCode.NAMESPACE_STATUS_INVALID.getCode(), "namespace has no published version"));
        String before =
                namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        namespace.setStatus(NamespaceStatusEnum.ENABLED.getCode());
        namespaceService.update(namespace);
        createChangeLog(id, namespace.getCurrentVersion(), NamespaceChangeActionEnum.ENABLE, before,
                namespaceSnapshotSupport.snapshot(
                        requireNamespace(id), namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion())));
        notifyChange(namespace.getNamespaceCode(), namespace.getCurrentVersion(), NamespaceChangeActionEnum.ENABLE);
        return toResponse(requireNamespace(id));
    }

    /**
     * 停用 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse disableNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        String before =
                namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        namespace.setStatus(NamespaceStatusEnum.DISABLED.getCode());
        namespaceService.update(namespace);
        createChangeLog(id, namespace.getCurrentVersion(), NamespaceChangeActionEnum.DISABLE, before,
                namespaceSnapshotSupport.snapshot(
                        requireNamespace(id), namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion())));
        notifyChange(namespace.getNamespaceCode(), namespace.getCurrentVersion(), NamespaceChangeActionEnum.DISABLE);
        return toResponse(requireNamespace(id));
    }

    /**
     * 回滚 Namespace 配置版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse rollbackNamespace(Long id, Integer version) {
        NamespaceDO namespace = requireNamespace(id);
        NamespaceConfigDO targetConfig = namespaceConfigService.getPublishedConfig(id, version);
        Assert.notNull(targetConfig, BizException.supplier(BusinessErrorCode.NAMESPACE_VERSION_NOT_FOUND));
        String before =
                namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        namespace.setCurrentVersion(version);
        namespaceService.update(namespace);
        createChangeLog(id, version, NamespaceChangeActionEnum.ROLLBACK, before,
                namespaceSnapshotSupport.snapshot(namespace, targetConfig));
        notifyChange(namespace.getNamespaceCode(), version, NamespaceChangeActionEnum.ROLLBACK);
        return toResponse(requireNamespace(id));
    }

    /**
     * 删除 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        Assert.isFalse(NamespaceStatusEnum.ENABLED.getCode().equals(namespace.getStatus()), BusinessErrorCode.NAMESPACE_DELETE_FORBIDDEN);
        String before = namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getEditingConfig(id));
        namespaceService.markDeleted(id);
        createChangeLog(id, namespace.getCurrentVersion(), NamespaceChangeActionEnum.DELETE, before, null);
        notifyChange(namespace.getNamespaceCode(), namespace.getCurrentVersion(), NamespaceChangeActionEnum.DELETE);
    }

    /**
     * 查询 Namespace 变更日志。
     */
    public List<NamespaceChangeLogResponse> listChangeLogs(Long id) {
        requireNamespace(id);
        return namespaceChangeLogService.listByNamespaceId(id).stream()
                .map(this::toChangeLogResponse)
                .toList();
    }

    /**
     * 查询 Namespace 实例状态。
     */
    public List<ServiceInstanceConfigStatusResponse> listInstanceStatuses(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        return serviceInstanceConfigStatusService.listByNamespaceCode(namespace.getNamespaceCode()).stream()
                .map(this::toInstanceStatusResponse)
                .toList();
    }

    private NamespaceDO requireNamespace(Long id) {
        NamespaceDO namespace = namespaceService.getById(id);
        Assert.notNull(namespace, BizException.supplier(BusinessErrorCode.NAMESPACE_NOT_FOUND));
        return namespace;
    }

    private void createChangeLog(
            Long namespaceId, Integer version, NamespaceChangeActionEnum action, String beforeJson, String afterJson) {
        NamespaceChangeLogDO changeLog = NamespaceChangeLogDO.builder()
                .namespaceId(namespaceId)
                .version(version == null ? 0 : version)
                .actionType(action.getCode())
                .beforeJson(beforeJson)
                .afterJson(afterJson)
                .operator(AdminSecurityContextUtils.currentUsername())
                .build();
        namespaceChangeLogService.create(changeLog);
    }

    private NamespaceResponse toResponse(NamespaceDO namespace) {
        NamespaceConfigDO draftConfig = namespaceConfigService.getEditingConfig(namespace.getId());
        NamespaceConfigDO publishedConfig = namespace.getCurrentVersion() != null && namespace.getCurrentVersion() > 0
                ? namespaceConfigService.getPublishedConfig(namespace.getId(), namespace.getCurrentVersion())
                : null;
        JsonNode draftConfigNode = draftConfig == null ? null : namespaceSnapshotSupport.readJson(draftConfig.getConfigJson());
        JsonNode publishedConfigNode = publishedConfig == null
                ? null
                : namespaceSnapshotSupport.readJson(publishedConfig.getConfigJson());
        return NamespaceConvert.INSTANCE.toResponse(namespace, draftConfigNode, publishedConfigNode);
    }

    private NamespaceChangeLogResponse toChangeLogResponse(NamespaceChangeLogDO changeLog) {
        return NamespaceConvert.INSTANCE.toChangeLogResponse(
                changeLog,
                namespaceSnapshotSupport.readJsonSafely(changeLog.getBeforeJson()),
                namespaceSnapshotSupport.readJsonSafely(changeLog.getAfterJson()));
    }

    private ServiceInstanceConfigStatusResponse toInstanceStatusResponse(ServiceInstanceConfigStatusDO status) {
        return NamespaceConvert.INSTANCE.toInstanceStatusResponse(status);
    }

    private String normalizeRemark(String remark) {
        return StringUtils.hasText(remark) ? remark.trim() : null;
    }

    private void notifyChange(String namespaceCode, Integer version, NamespaceChangeActionEnum action) {
        namespaceChangePublisher.publish(NamespaceChangeMessage.builder()
                .namespaceCode(namespaceCode)
                .version(version)
                .eventType(action.getName())
                .publishTime(java.time.LocalDateTime.now())
                .build());
    }

}
