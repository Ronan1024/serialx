package com.ronan.serialx.admin.service;

import java.util.List;
import java.util.Map;

import com.ronan.serialx.infra.entity.NamespaceConfigDO;

/**
 * Namespace 配置数据访问服务。
 */
public interface NamespaceConfigService {

    /**
     * 查询 Namespace 的最新编辑态配置，供管理端展示或继续编辑。
     *
     * @param namespaceId Namespace 主键
     * @return 按主键倒序取得的首条 EDITING 记录；不存在时返回 null，不回退到已发布配置
     */
    NamespaceConfigDO getEditingConfig(Long namespaceId);

    /**
     * 批量查询 Namespace 编辑态配置，按主键倒序供业务层选取各 Namespace 的最新草稿。
     *
     * @param namespaceIdList Namespace 主键列表
     * @return 匹配的 EDITING 记录，按主键倒序排列；空输入或无记录时返回空列表
     */
    List<NamespaceConfigDO> listEditingConfigs(List<Long> namespaceIdList);

    /**
     * 批量查询各 Namespace 指定版本的已发布配置，版本之间不可交叉匹配。
     *
     * @param namespaceVersions Namespace 主键到目标版本号的映射，忽略 null 或非正数版本
     * @return 匹配的 PUBLISHED 记录，按主键倒序排列；空输入或无有效版本时返回空列表
     */
    List<NamespaceConfigDO> listPublishedConfigsByVersions(Map<Long, Integer> namespaceVersions);

    /**
     * 查询指定版本配置。
     */
    NamespaceConfigDO getByNamespaceIdAndVersion(Long namespaceId, Integer version);

    /**
     * 查询 Namespace 指定版本的已发布配置。
     *
     * <p>展示当前发布配置时应传入 Namespace.currentVersion；历史最大版本可能不是回滚后的目标版本。
     *
     * @param namespaceId Namespace 主键
     * @param version 目标版本号；null 或非正数表示没有可查询的发布版本
     * @return 对应版本的 PUBLISHED 记录；版本无效或记录不存在时返回 null
     */
    NamespaceConfigDO getPublishedConfig(Long namespaceId, Integer version);

    /**
     * 查询所有已发布版本。
     */
    List<NamespaceConfigDO> listPublishedConfigs(Long namespaceId);

    /**
     * 查询历史最大已发布版本，没有发布记录时返回 0。
     */
    int getMaxPublishedVersion(Long namespaceId);

    /**
     * 创建配置记录。
     */
    NamespaceConfigDO create(NamespaceConfigDO namespaceConfig);

    /**
     * 更新配置记录。
     */
    void update(NamespaceConfigDO namespaceConfig);
}
