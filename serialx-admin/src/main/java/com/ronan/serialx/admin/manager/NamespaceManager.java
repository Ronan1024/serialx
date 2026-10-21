package com.ronan.serialx.admin.manager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.ronan.serialx.admin.convert.NamespaceConvert;
import com.ronan.serialx.admin.dto.*;
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

    private final NamespaceService namespaceService;


    private final NamespaceConfigService namespaceConfigService;

    private final NamespaceChangeLogService namespaceChangeLogService;


    private final ServiceInstanceConfigStatusService serviceInstanceConfigStatusService;

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
     * 查询 Namespace 管理列表，并为当前页记录补齐草稿与当前发布配置。
     *
     * <p>分页总数沿用主记录查询结果，不以当前页条数代替；配置装配不改变分页顺序。
     * 本方法为只读编排，不发布配置，也不探测 service 实例是否已同步。
     *
     * @param request 分页及筛选条件，pageNo 与 pageSize 须非 null
     * @return 筛选后的分页结果，每条记录使用与详情接口相同的配置展示口径
     */
    public PageResult<NamespaceResponse> pageNamespaces(NamespacePageQueryRequest request) {
        Page<NamespaceDO> page = namespaceService.pageNamespaces(request.getPageNo(), request.getPageSize(), request.getKeyword(), request.getStatus(), request.getIdMode());
        List<NamespaceResponse> records = toResponses(page.getRecords());
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 查询 Namespace 详情。
     */
    public NamespaceResponse getNamespace(Long id) {
        return loadResponse(requireNamespace(id));
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

        createChangeLog(namespace.getId(), 0, NamespaceChangeActionEnum.CREATE, null, namespaceSnapshotSupport.snapshot(namespace, draftConfig));
        return loadResponse(namespace);
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
        return loadResponse(latest);
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
        return loadResponse(latest);
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

        int maxVersion = namespaceConfigService.getMaxPublishedVersion(id);
        Assert.isTrue(maxVersion < Integer.MAX_VALUE,
                BizException.supplier(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED.getCode(), "namespace version exhausted"));
        int nextVersion = maxVersion + 1;

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
        return loadResponse(requireNamespace(id));
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
            return loadResponse(namespace);
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
        return loadResponse(requireNamespace(id));
    }

    /**
     * 停用 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse disableNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        String before = namespaceSnapshotSupport.snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        if (NamespaceStatusEnum.DISABLED.getCode().equals(namespace.getStatus())) {
            return loadResponse(namespace);
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
        return loadResponse(requireNamespace(id));
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
            return loadResponse(namespace);
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
        return loadResponse(requireNamespace(id));
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

    /**
     * 批量加载当前页的两类配置，保持主记录顺序，避免转换循环逐条访问数据库。
     *
     * @param namespaces 当前页 Namespace 主记录
     * @return 按原顺序装配的管理端响应；空页不查询配置
     */
    private List<NamespaceResponse> toResponses(List<NamespaceDO> namespaces) {
        if (namespaces.isEmpty()) {
            return List.of();
        }
        List<Long> namespaceIds = namespaces.stream().map(NamespaceDO::getId).toList();
        Map<Long, Integer> versions = namespaces.stream()
                .filter(namespace -> namespace.getCurrentVersion() != null && namespace.getCurrentVersion() > 0)
                .collect(Collectors.toMap(NamespaceDO::getId, NamespaceDO::getCurrentVersion));
        Map<Long, NamespaceConfigDO> drafts = namespaceConfigService.listEditingConfigs(namespaceIds).stream()
                .collect(Collectors.toMap(NamespaceConfigDO::getNamespaceId, Function.identity(), (latest, older) -> latest));
        Map<Long, NamespaceConfigDO> published = versions.isEmpty() ? Map.of()
                : namespaceConfigService.listPublishedConfigsByVersions(versions).stream()
                .collect(Collectors.toMap(NamespaceConfigDO::getNamespaceId, Function.identity(), (latest, older) -> latest));
        return namespaces.stream()
                .map(namespace -> toResponse(namespace, drafts.get(namespace.getId()), published.get(namespace.getId())))
                .toList();
    }

    /**
     * 为详情及单条管理操作加载配置，单条查询不进入分页装配循环。
     *
     * @param namespace 待展示的 Namespace 主记录
     * @return 与分页列表字段语义一致的管理端响应
     */
    private NamespaceResponse loadResponse(NamespaceDO namespace) {
        return toResponse(namespace, namespaceConfigService.getEditingConfig(namespace.getId()), namespaceConfigService.getPublishedConfig(namespace.getId(), namespace.getCurrentVersion()));
    }

    /**
     * 将已加载的主记录与两类配置转换为响应，不访问数据库。
     *
     * <p>草稿取最新 EDITING 记录；发布配置严格按 currentVersion 读取，不能用历史最大版本替代，
     * 否则回滚后会展示错误配置。两者独立返回，缺失时不互相回填。
     *
     * @param namespace       待展示的 Namespace 主记录
     * @param draftConfig     最新编辑态配置，不存在时为 null
     * @param publishedConfig currentVersion 对应的发布配置，不存在时为 null
     * @return 基础信息、状态名称、草稿配置与当前版本发布配置
     * @throws BizException 配置 JSON 无法解析，错误码为 NAMESPACE_CONFIG_INVALID
     */
    private NamespaceResponse toResponse(NamespaceDO namespace, NamespaceConfigDO draftConfig, NamespaceConfigDO publishedConfig) {
        JsonNode draftConfigNode = draftConfig == null ? null : namespaceSnapshotSupport.readJson(draftConfig.getConfigJson());
        JsonNode publishedConfigNode = publishedConfig == null ? null : namespaceSnapshotSupport.readJson(publishedConfig.getConfigJson());
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
