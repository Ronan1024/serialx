package com.ronan.serialx.admin.manager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.admin.convert.NamespaceConvert;
import com.ronan.serialx.admin.dto.NamespaceChangeLogResponse;
import com.ronan.serialx.admin.dto.NamespaceCreateRequest;
import com.ronan.serialx.admin.dto.NamespaceDraftConfigRequest;
import com.ronan.serialx.admin.dto.NamespaceResponse;
import com.ronan.serialx.admin.dto.NamespaceUpdateRequest;
import com.ronan.serialx.admin.dto.ServiceInstanceConfigStatusResponse;
import com.ronan.serialx.admin.security.AdminUserPrincipal;
import com.ronan.serialx.common.model.NamespaceChangeMessage;
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
import com.ronan.serialx.common.response.PageResult;
import com.ronan.serialx.infra.entity.NamespaceChangeLogDO;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
     * JSON 序列化组件。
     */
    private final ObjectMapper objectMapper;

    /**
     * Namespace 变更通知发布器。
     */
    private final NamespaceChangePublisher namespaceChangePublisher;

    /**
     * 分页查询 Namespace。
     */
    public PageResult<NamespaceResponse> pageNamespaces(
            long pageNo, long pageSize, String keyword, Integer status, Integer idMode) {
        validateStatus(status);
        validateIdMode(idMode);
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
        validateIdMode(request.getIdMode());
        validateNamespaceCode(request.getNamespaceCode());
        validateConfig(request.getIdMode(), request.getConfig());
        if (namespaceService.existsByCode(request.getNamespaceCode().trim())) {
            throw BizException.conflict(
                    BusinessErrorCode.NAMESPACE_CODE_EXISTS, BusinessErrorCode.NAMESPACE_CODE_EXISTS.getMessage());
        }
        NamespaceDO namespace = new NamespaceDO();
        namespace.setNamespaceCode(request.getNamespaceCode().trim());
        namespace.setNamespaceName(request.getNamespaceName().trim());
        namespace.setIdMode(request.getIdMode());
        namespace.setStatus(NamespaceStatusEnum.DRAFT.getCode());
        namespace.setOwner(request.getOwner().trim());
        namespace.setRemark(normalizeRemark(request.getRemark()));
        namespace.setCurrentVersion(0);
        namespaceService.create(namespace);

        NamespaceConfigDO draftConfig = buildDraftConfig(namespace.getId(), request.getIdMode(), request.getConfig());
        namespaceConfigService.create(draftConfig);
        createChangeLog(namespace.getId(), 0, NamespaceChangeActionEnum.CREATE, null, snapshot(namespace, draftConfig));
        return toResponse(requireNamespace(namespace.getId()));
    }

    /**
     * 更新 Namespace 基础信息。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse updateNamespace(Long id, NamespaceUpdateRequest request) {
        NamespaceDO namespace = requireNamespace(id);
        String before = snapshot(namespace, namespaceConfigService.getEditingConfig(id));
        namespace.setNamespaceName(request.getNamespaceName().trim());
        namespace.setOwner(request.getOwner().trim());
        namespace.setRemark(normalizeRemark(request.getRemark()));
        namespaceService.update(namespace);
        NamespaceDO latest = requireNamespace(id);
        createChangeLog(id, latest.getCurrentVersion(), NamespaceChangeActionEnum.UPDATE, before, snapshot(latest,
                namespaceConfigService.getEditingConfig(id)));
        return toResponse(latest);
    }

    /**
     * 保存 Namespace 草稿配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse saveDraftConfig(Long id, NamespaceDraftConfigRequest request) {
        NamespaceDO namespace = requireNamespace(id);
        validateIdMode(request.getIdMode());
        validateConfig(request.getIdMode(), request.getConfig());
        String before = snapshot(namespace, namespaceConfigService.getEditingConfig(id));
        namespace.setIdMode(request.getIdMode());
        namespaceService.update(namespace);
        NamespaceConfigDO editingConfig = namespaceConfigService.getEditingConfig(id);
        if (editingConfig == null) {
            editingConfig = buildDraftConfig(id, request.getIdMode(), request.getConfig());
            namespaceConfigService.create(editingConfig);
        } else {
            editingConfig.setIdMode(request.getIdMode());
            editingConfig.setConfigJson(writeJson(request.getConfig()));
            editingConfig.setConfigChecksum(calculateChecksum(editingConfig.getConfigJson()));
            namespaceConfigService.update(editingConfig);
        }
        NamespaceDO latest = requireNamespace(id);
        createChangeLog(id, latest.getCurrentVersion(), NamespaceChangeActionEnum.SAVE_DRAFT, before,
                snapshot(latest, namespaceConfigService.getEditingConfig(id)));
        return toResponse(latest);
    }

    /**
     * 发布 Namespace 配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse publishNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        NamespaceConfigDO editingConfig = namespaceConfigService.getEditingConfig(id);
        if (editingConfig == null) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID, "namespace draft config not found");
        }
        validateConfig(editingConfig.getIdMode(), readJson(editingConfig.getConfigJson()));
        int nextVersion = namespace.getCurrentVersion() + 1;
        String before = snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        NamespaceConfigDO publishedConfig = new NamespaceConfigDO();
        publishedConfig.setNamespaceId(id);
        publishedConfig.setVersion(nextVersion);
        publishedConfig.setIdMode(editingConfig.getIdMode());
        publishedConfig.setConfigJson(editingConfig.getConfigJson());
        publishedConfig.setConfigChecksum(editingConfig.getConfigChecksum());
        publishedConfig.setPublishStatus(NamespacePublishStatusEnum.PUBLISHED.getCode());
        publishedConfig.setPublishedBy(currentOperator());
        publishedConfig.setPublishedAt(java.time.LocalDateTime.now());
        namespaceConfigService.create(publishedConfig);

        namespace.setCurrentVersion(nextVersion);
        if (NamespaceStatusEnum.DRAFT.getCode().equals(namespace.getStatus())) {
            namespace.setStatus(NamespaceStatusEnum.ENABLED.getCode());
        }
        namespaceService.update(namespace);
        createChangeLog(id, nextVersion, NamespaceChangeActionEnum.PUBLISH, before, snapshot(namespace, publishedConfig));
        notifyChange(namespace.getNamespaceCode(), nextVersion, NamespaceChangeActionEnum.PUBLISH);
        return toResponse(requireNamespace(id));
    }

    /**
     * 启用 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse enableNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        if (namespace.getCurrentVersion() <= 0) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_STATUS_INVALID, "namespace has no published version");
        }
        String before = snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        namespace.setStatus(NamespaceStatusEnum.ENABLED.getCode());
        namespaceService.update(namespace);
        createChangeLog(id, namespace.getCurrentVersion(), NamespaceChangeActionEnum.ENABLE, before,
                snapshot(requireNamespace(id), namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion())));
        notifyChange(namespace.getNamespaceCode(), namespace.getCurrentVersion(), NamespaceChangeActionEnum.ENABLE);
        return toResponse(requireNamespace(id));
    }

    /**
     * 停用 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public NamespaceResponse disableNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        String before = snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        namespace.setStatus(NamespaceStatusEnum.DISABLED.getCode());
        namespaceService.update(namespace);
        createChangeLog(id, namespace.getCurrentVersion(), NamespaceChangeActionEnum.DISABLE, before,
                snapshot(requireNamespace(id), namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion())));
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
        if (targetConfig == null) {
            throw BizException.notFound(
                    BusinessErrorCode.NAMESPACE_VERSION_NOT_FOUND,
                    BusinessErrorCode.NAMESPACE_VERSION_NOT_FOUND.getMessage());
        }
        String before = snapshot(namespace, namespaceConfigService.getPublishedConfig(id, namespace.getCurrentVersion()));
        namespace.setCurrentVersion(version);
        namespaceService.update(namespace);
        createChangeLog(id, version, NamespaceChangeActionEnum.ROLLBACK, before, snapshot(namespace, targetConfig));
        notifyChange(namespace.getNamespaceCode(), version, NamespaceChangeActionEnum.ROLLBACK);
        return toResponse(requireNamespace(id));
    }

    /**
     * 删除 Namespace。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteNamespace(Long id) {
        NamespaceDO namespace = requireNamespace(id);
        if (NamespaceStatusEnum.ENABLED.getCode().equals(namespace.getStatus())) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_DELETE_FORBIDDEN, BusinessErrorCode.NAMESPACE_DELETE_FORBIDDEN.getMessage());
        }
        String before = snapshot(namespace, namespaceConfigService.getEditingConfig(id));
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
        if (namespace == null) {
            throw BizException.notFound(
                    BusinessErrorCode.NAMESPACE_NOT_FOUND, BusinessErrorCode.NAMESPACE_NOT_FOUND.getMessage());
        }
        return namespace;
    }

    private void validateStatus(Integer status) {
        if (status != null && !NamespaceStatusEnum.contains(status)) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_STATUS_INVALID, BusinessErrorCode.NAMESPACE_STATUS_INVALID.getMessage());
        }
    }

    private void validateIdMode(Integer idMode) {
        if (idMode != null && !NamespaceIdModeEnum.contains(idMode)) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID, "namespace id mode invalid");
        }
    }

    private void validateNamespaceCode(String namespaceCode) {
        if (!StringUtils.hasText(namespaceCode) || !namespaceCode.trim().matches("^[a-zA-Z][a-zA-Z0-9_\\-]{2,63}$")) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID,
                    "namespace code must start with letter and contain 3-64 letters, numbers, '_' or '-'");
        }
    }

    /**
     * 校验配置完整性。
     */
    private void validateConfig(Integer idMode, JsonNode config) {
        if (config == null || config.isNull()) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID, "namespace config is required");
        }
        NamespaceIdModeEnum mode = NamespaceIdModeEnum.fromCode(idMode);
        if (mode == NamespaceIdModeEnum.SEGMENT) {
            requirePositive(config, "stepSize");
            requireNonNegative(config, "initialValue");
            requirePositive(config, "cacheSegments");
        }
    }

    private void requirePositive(JsonNode config, String fieldName) {
        if (!config.has(fieldName) || !config.get(fieldName).canConvertToInt() || config.get(fieldName).asInt() <= 0) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID, fieldName + " must be greater than zero");
        }
    }

    private void requireNonNegative(JsonNode config, String fieldName) {
        if (!config.has(fieldName) || !config.get(fieldName).canConvertToLong() || config.get(fieldName).asLong() < 0) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID, fieldName + " must be greater than or equal to zero");
        }
    }

    private NamespaceConfigDO buildDraftConfig(Long namespaceId, Integer idMode, JsonNode config) {
        NamespaceConfigDO draftConfig = new NamespaceConfigDO();
        draftConfig.setNamespaceId(namespaceId);
        draftConfig.setVersion(0);
        draftConfig.setIdMode(idMode);
        draftConfig.setConfigJson(writeJson(config));
        draftConfig.setConfigChecksum(calculateChecksum(draftConfig.getConfigJson()));
        draftConfig.setPublishStatus(NamespacePublishStatusEnum.EDITING.getCode());
        return draftConfig;
    }

    private void createChangeLog(
            Long namespaceId, Integer version, NamespaceChangeActionEnum action, String beforeJson, String afterJson) {
        NamespaceChangeLogDO changeLog = new NamespaceChangeLogDO();
        changeLog.setNamespaceId(namespaceId);
        changeLog.setVersion(version == null ? 0 : version);
        changeLog.setActionType(action.getCode());
        changeLog.setBeforeJson(beforeJson);
        changeLog.setAfterJson(afterJson);
        changeLog.setOperator(currentOperator());
        namespaceChangeLogService.create(changeLog);
    }

    private NamespaceResponse toResponse(NamespaceDO namespace) {
        NamespaceConfigDO draftConfig = namespaceConfigService.getEditingConfig(namespace.getId());
        NamespaceConfigDO publishedConfig = namespace.getCurrentVersion() != null && namespace.getCurrentVersion() > 0
                ? namespaceConfigService.getPublishedConfig(namespace.getId(), namespace.getCurrentVersion())
                : null;
        return NamespaceConvert.INSTANCE.toResponse(
                namespace, draftConfig == null ? null : readJson(draftConfig.getConfigJson()), publishedConfig == null ? null : readJson(publishedConfig.getConfigJson()));
    }

    private NamespaceChangeLogResponse toChangeLogResponse(NamespaceChangeLogDO changeLog) {
        return NamespaceConvert.INSTANCE.toChangeLogResponse(
                changeLog,
                readJsonSafely(changeLog.getBeforeJson()),
                readJsonSafely(changeLog.getAfterJson()));
    }

    private ServiceInstanceConfigStatusResponse toInstanceStatusResponse(ServiceInstanceConfigStatusDO status) {
        return NamespaceConvert.INSTANCE.toInstanceStatusResponse(status);
    }

    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminUserPrincipal principal) {
            return principal.getUsername();
        }
        return "system";
    }

    private String normalizeRemark(String remark) {
        return StringUtils.hasText(remark) ? remark.trim() : null;
    }

    private String snapshot(NamespaceDO namespace, NamespaceConfigDO config) {
        try {
            return objectMapper.writeValueAsString(new SnapshotView(namespace, config == null ? null : readJson(config.getConfigJson())));
        } catch (Exception ex) {
            throw BizException.system(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED, "serialize namespace snapshot failed");
        }
    }

    private String writeJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception ex) {
            throw BizException.badRequest(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID, "namespace config serialize failed");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw BizException.system(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID, "namespace config deserialize failed");
        }
    }

    private JsonNode readJsonSafely(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return readJson(json);
    }

    private String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw BizException.system(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED, "checksum algorithm unavailable");
        }
    }

    private void notifyChange(String namespaceCode, Integer version, NamespaceChangeActionEnum action) {
        namespaceChangePublisher.publish(NamespaceChangeMessage.builder()
                .namespaceCode(namespaceCode)
                .version(version)
                .eventType(action.getName())
                .publishTime(java.time.LocalDateTime.now())
                .build());
    }

    /**
     * Namespace 快照视图。
     */
    private record SnapshotView(NamespaceDO namespace, JsonNode config) {
    }
}
