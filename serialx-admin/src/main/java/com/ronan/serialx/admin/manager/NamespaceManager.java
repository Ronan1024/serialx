package com.ronan.serialx.admin.manager;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.ronan.serialx.admin.convert.NamespaceConvert;
import com.ronan.serialx.admin.dto.NamespaceChangeLogResponse;
import com.ronan.serialx.admin.dto.NamespaceCreateRequest;
import com.ronan.serialx.admin.dto.NamespaceDraftConfigRequest;
import com.ronan.serialx.admin.dto.NamespaceResponse;
import com.ronan.serialx.admin.dto.NamespaceUpdateRequest;
import com.ronan.serialx.admin.dto.NamespaceVersionResponse;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

        Assert.isFalse(status != null && !NamespaceStatusEnum.contains(status), BusinessErrorCode.NAMESPACE_STATUS_INVALID);
        Assert.isFalse(idMode != null && !NamespaceIdModeEnum.contains(idMode), BizException.supplier(BusinessErrorCode.NAMESPACE_CONFIG_INVALID));

        Page<NamespaceDO> page = namespaceService.pageNamespaces(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100), keyword, status, idMode);

        List<NamespaceResponse> records = page.getRecords().stream().map(this::toResponse).toList();
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
        String namespaceCode = request.getNamespaceCode();
        Assert.isFalse(!ObjectUtils.isEmpty(request.getIdMode()) && !NamespaceIdModeEnum.contains(request.getIdMode()),
                BizException.supplier(BusinessErrorCode.NAMESPACE_CONFIG_INVALID));

        Assert.isTrue(StringUtils.hasText(namespaceCode) && namespaceCode.trim().matches("^[a-zA-Z][a-zA-Z0-9_\\-]{2,63}$"),
                BizException.supplier(BusinessErrorCode.NAMESPACE_CONFIG_INVALID));

        JsonNode normalizedConfig = namespaceConfigHandlerRegistry.normalize(request.getIdMode(), request.getConfig());

        Assert.isFalse(namespaceService.existsByCode(namespaceCode.trim()), BusinessErrorCode.NAMESPACE_CODE_EXISTS);

        request.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        NamespaceDO namespace = NamespaceConvert.INSTANCE.toNamespaceDO(request);
        try {
            namespaceService.create(namespace);
        } catch (DuplicateKeyException ex) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CODE_EXISTS);
        }

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
                BizException.supplier(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "找不到命名空间草图配置"));

        namespaceConfigHandlerRegistry.normalize(
                editingConfig.getIdMode(), namespaceSnapshotSupport.readJson(editingConfig.getConfigJson()));

        int nextVersion = namespace.getCurrentVersion() + 1;

        String before =
                namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));

        NamespaceConfigDO publishedConfig = NamespaceConvert.INSTANCE.toNamespaceConfigDO(id, nextVersion, editingConfig);
        publishedConfig.setPublishedBy(AdminSecurityContextUtils.currentUsername());
        try {
            namespaceConfigService.create(publishedConfig);
        } catch (DuplicateKeyException ex) {
            throw new BizException(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED.getCode(), "namespace publish conflict");
        }

        Integer nextStatus = NamespaceStatusEnum.DRAFT.getCode().equals(namespace.getStatus())
                ? NamespaceStatusEnum.ENABLED.getCode()
                : namespace.getStatus();

        NamespaceDO publishedNamespace = copyNamespaceForSnapshot(namespace);
        publishedNamespace.setStatus(nextStatus);
        publishedNamespace.setCurrentVersion(nextVersion);
        String snapshot = namespaceSnapshotSupport.snapshot(publishedNamespace, publishedConfig);
        boolean updated = namespaceService.updateVersionAndStatus(
                id,
                namespace.getCurrentVersion(),
                namespace.getStatus(),
                nextVersion,
                nextStatus);
        assertUpdated(updated, BusinessErrorCode.NAMESPACE_PUBLISH_FAILED);

        createChangeLog(id, nextVersion, NamespaceChangeActionEnum.PUBLISH, before, snapshot);

        notifyChangeAfterCommit(namespace.getNamespaceCode(), nextVersion, NamespaceChangeActionEnum.PUBLISH);
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
        if (NamespaceStatusEnum.ENABLED.getCode().equals(namespace.getStatus())) {
            return toResponse(namespace);
        }
        boolean updated = namespaceService.updateStatus(
                id,
                namespace.getCurrentVersion(),
                namespace.getStatus(),
                NamespaceStatusEnum.ENABLED.getCode());
        assertUpdated(updated, BusinessErrorCode.NAMESPACE_STATUS_INVALID);
        createChangeLog(id, namespace.getCurrentVersion(), NamespaceChangeActionEnum.ENABLE, before,
                namespaceSnapshotSupport.snapshot(
                        requireNamespace(id), namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion())));
        notifyChangeAfterCommit(namespace.getNamespaceCode(), namespace.getCurrentVersion(), NamespaceChangeActionEnum.ENABLE);
        return toResponse(requireNamespace(id));
    }

    /**
     * 停用 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse disableNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        String before = namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        if (NamespaceStatusEnum.DISABLED.getCode().equals(namespace.getStatus())) {
            return toResponse(namespace);
        }
        boolean updated = namespaceService.updateStatus(
                id,
                namespace.getCurrentVersion(),
                namespace.getStatus(),
                NamespaceStatusEnum.DISABLED.getCode());
        assertUpdated(updated, BusinessErrorCode.NAMESPACE_STATUS_INVALID);
        createChangeLog(id, namespace.getCurrentVersion(), NamespaceChangeActionEnum.DISABLE, before,
                namespaceSnapshotSupport.snapshot(
                        requireNamespace(id), namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion())));
        notifyChangeAfterCommit(namespace.getNamespaceCode(), namespace.getCurrentVersion(), NamespaceChangeActionEnum.DISABLE);
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
        if (version.equals(namespace.getCurrentVersion())) {
            return toResponse(namespace);
        }
        String before =
                namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        NamespaceDO rollbackNamespace = copyNamespaceForSnapshot(namespace);
        rollbackNamespace.setCurrentVersion(version);
        boolean updated = namespaceService.updateCurrentVersion(id, namespace.getCurrentVersion(), namespace.getStatus(), version);
        assertUpdated(updated, BusinessErrorCode.NAMESPACE_ROLLBACK_FAILED);
        createChangeLog(id, version, NamespaceChangeActionEnum.ROLLBACK, before,
                namespaceSnapshotSupport.snapshot(rollbackNamespace, targetConfig));
        notifyChangeAfterCommit(namespace.getNamespaceCode(), version, NamespaceChangeActionEnum.ROLLBACK);
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
        boolean updated = namespaceService.markDeleted(id, namespace.getCurrentVersion(), namespace.getStatus());
        assertUpdated(updated, BusinessErrorCode.NAMESPACE_STATUS_INVALID);
        createChangeLog(id, namespace.getCurrentVersion(), NamespaceChangeActionEnum.DELETE, before, null);
        notifyChangeAfterCommit(namespace.getNamespaceCode(), namespace.getCurrentVersion(), NamespaceChangeActionEnum.DELETE);
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
     * 查询 Namespace 已发布版本。
     */
    public List<NamespaceVersionResponse> listPublishedVersions(Long id) {
        requireNamespace(id);
        return namespaceConfigService.listPublishedConfigs(id).stream()
                .map(this::toVersionResponse)
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


    /**
     * 创建变更记录信息
     *
     * @param namespaceId 命名空间ID
     * @param version     版本号
     * @param action      变更动作
     * @param beforeJson  变更前的JSON数据
     * @param afterJson   变更后的JSON数据
     */
    private void createChangeLog(Long namespaceId, Integer version, NamespaceChangeActionEnum action, String beforeJson, String afterJson) {
        NamespaceChangeLogDO changeLog = NamespaceChangeLogDO.builder()
                .namespaceId(namespaceId).version(version == null ? 0 : version)
                .actionType(action.getCode()).beforeJson(beforeJson)
                .afterJson(afterJson).operator(AdminSecurityContextUtils.currentUsername())
                .build();
        namespaceChangeLogService.create(changeLog);
    }

    private NamespaceResponse toResponse(NamespaceDO namespace) {
        NamespaceConfigDO draftConfig = namespaceConfigService.getEditingConfig(namespace.getId());
        NamespaceConfigDO publishedConfig = namespaceConfigService.getPublishedConfig(namespace.getId(), namespace.getCurrentVersion());

        JsonNode draftConfigNode = draftConfig == null ? null : namespaceSnapshotSupport.readJson(draftConfig.getConfigJson());

        JsonNode publishedConfigNode = Optional.ofNullable(publishedConfig)
                .map(e -> namespaceSnapshotSupport.readJson(e.getConfigJson())).orElse(null);


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

    private NamespaceVersionResponse toVersionResponse(NamespaceConfigDO config) {
        NamespaceIdModeEnum idModeEnum = NamespaceIdModeEnum.fromCode(config.getIdMode());
        return NamespaceVersionResponse.builder()
                .version(config.getVersion())
                .idMode(config.getIdMode())
                .idModeName(idModeEnum == null ? null : idModeEnum.getName())
                .config(namespaceSnapshotSupport.readJson(config.getConfigJson()))
                .configChecksum(config.getConfigChecksum())
                .operator(config.getPublishedBy())
                .publishedAt(config.getPublishedAt())
                .createdAt(config.getCreatedAt())
                .build();
    }

    private String normalizeRemark(String remark) {
        return StringUtils.hasText(remark) ? remark.trim() : null;
    }

    /**
     * 发布命名空间变更通知
     *
     * @param namespaceCode 命名空间代码
     * @param version       版本号
     * @param action        变更动作
     */
    private void notifyChangeAfterCommit(String namespaceCode, Integer version, NamespaceChangeActionEnum action) {
        Runnable publishTask = () -> namespaceChangePublisher.publish(NamespaceChangeMessage.builder()
                .namespaceCode(namespaceCode)
                .version(version)
                .eventType(action.getName())
                .publishTime(java.time.LocalDateTime.now())
                .build());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishTask.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishTask.run();
            }
        });
    }

    private void assertUpdated(boolean updated, BusinessErrorCode errorCode) {
        Assert.isTrue(updated, BizException.supplier(errorCode.getCode(), "namespace has been changed by another request"));
    }

    private NamespaceDO copyNamespaceForSnapshot(NamespaceDO namespace) {
        NamespaceDO copied = new NamespaceDO();
        copied.setId(namespace.getId());
        copied.setNamespaceCode(namespace.getNamespaceCode());
        copied.setNamespaceName(namespace.getNamespaceName());
        copied.setIdMode(namespace.getIdMode());
        copied.setStatus(namespace.getStatus());
        copied.setOwner(namespace.getOwner());
        copied.setRemark(namespace.getRemark());
        copied.setCurrentVersion(namespace.getCurrentVersion());
        copied.setDeleted(namespace.getDeleted());
        copied.setCreatedAt(namespace.getCreatedAt());
        copied.setUpdatedAt(namespace.getUpdatedAt());
        return copied;
    }

}
