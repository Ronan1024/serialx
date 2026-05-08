package com.ronan.serialx.admin.service;

import java.util.List;

import com.ronan.serialx.infra.entity.NamespaceConfigDO;

/**
 * Namespace 配置数据访问服务。
 */
public interface NamespaceConfigService {

    /**
     * 查询当前草稿配置。
     */
    NamespaceConfigDO getEditingConfig(Long namespaceId);

    /**
     * 查询指定版本配置。
     */
    NamespaceConfigDO getByNamespaceIdAndVersion(Long namespaceId, Integer version);

    /**
     * 查询当前已发布配置。
     */
    NamespaceConfigDO getPublishedConfig(Long namespaceId, Integer version);

    /**
     * 查询所有已发布版本。
     */
    List<NamespaceConfigDO> listPublishedConfigs(Long namespaceId);

    /**
     * 创建配置记录。
     */
    NamespaceConfigDO create(NamespaceConfigDO namespaceConfig);

    /**
     * 更新配置记录。
     */
    void update(NamespaceConfigDO namespaceConfig);
}
