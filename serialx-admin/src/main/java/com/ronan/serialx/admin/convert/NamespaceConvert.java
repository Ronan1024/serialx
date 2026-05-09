package com.ronan.serialx.admin.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronan.serialx.admin.dto.NamespaceChangeLogResponse;
import com.ronan.serialx.admin.dto.NamespaceCreateRequest;
import com.ronan.serialx.admin.dto.NamespaceResponse;
import com.ronan.serialx.admin.dto.ServiceInstanceConfigStatusResponse;
import com.ronan.serialx.infra.entity.NamespaceChangeLogDO;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Namespace 相关对象转换 Mapper。
 */
@Mapper
public interface NamespaceConvert {

    NamespaceConvert INSTANCE = Mappers.getMapper(NamespaceConvert.class);

    /**
     * 将 Namespace 持久化对象转换为响应对象。
     */
    @Mapping(
            target = "idModeName",
            expression =
                    "java(com.ronan.serialx.common.enums.NamespaceIdModeEnum.fromCode(namespace.getIdMode()) == null ? null : com.ronan.serialx.common.enums.NamespaceIdModeEnum.fromCode(namespace.getIdMode()).getName())")
    @Mapping(
            target = "statusName",
            expression =
                    "java(com.ronan.serialx.common.enums.NamespaceStatusEnum.fromCode(namespace.getStatus()) == null ? null : com.ronan.serialx.common.enums.NamespaceStatusEnum.fromCode(namespace.getStatus()).getName())")
    @Mapping(target = "draftConfig", source = "draftConfig")
    @Mapping(target = "publishedConfig", source = "publishedConfig")
    NamespaceResponse toResponse(NamespaceDO namespace, JsonNode draftConfig, JsonNode publishedConfig);

    /**
     * 将 Namespace 变更日志持久化对象转换为响应对象。
     */
    @Mapping(
            target = "actionTypeName",
            expression =
                    "java(com.ronan.serialx.common.enums.NamespaceChangeActionEnum.fromCode(changeLog.getActionType()) == null ? null : com.ronan.serialx.common.enums.NamespaceChangeActionEnum.fromCode(changeLog.getActionType()).getName())")
    @Mapping(target = "beforeJson", source = "beforeJson")
    @Mapping(target = "afterJson", source = "afterJson")
    NamespaceChangeLogResponse toChangeLogResponse(
            NamespaceChangeLogDO changeLog, JsonNode beforeJson, JsonNode afterJson);

    /**
     * 将 Service 实例配置状态持久化对象转换为响应对象。
     */
    ServiceInstanceConfigStatusResponse toInstanceStatusResponse(ServiceInstanceConfigStatusDO status);

    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "currentVersion", expression = "java(0)")
    @Mapping(target = "owner", expression = "java(request.getOwner().trim())")
    @Mapping(target = "namespaceCode", expression = "java(request.getNamespaceCode().trim())")
    @Mapping(target = "namespaceName", expression = "java(request.getNamespaceName().trim())")
    @Mapping(target = "status", expression = "java(com.ronan.serialx.common.enums.NamespaceStatusEnum.DRAFT.getCode())")
    NamespaceDO toNamespaceDO(NamespaceCreateRequest request);

    @Mapping(target = "namespaceId", source = "id")
    @Mapping(target = "version", source = "nextVersion")
    @Mapping(target = "publishStatus", expression = "java(com.ronan.serialx.common.enums.NamespacePublishStatusEnum.PUBLISHED.getCode())")
    @Mapping(target = "publishedAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "publishedBy", ignore = true)
    NamespaceConfigDO toNamespaceConfigDO(Long id, int nextVersion, NamespaceConfigDO editingConfig);
}
