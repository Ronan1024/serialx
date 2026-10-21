package com.ronan.serialx.admin.service.impl;

import static com.ronan.serialx.infra.support.MybatisQueryUtils.selectFirst;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ronan.serialx.admin.service.NamespaceConfigService;
import com.ronan.serialx.common.enums.NamespacePublishStatusEnum;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.mapper.NamespaceConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Namespace 配置数据访问服务实现。
 */
@Service
@RequiredArgsConstructor
public class NamespaceConfigServiceImpl implements NamespaceConfigService {

    /**
     * Namespace 配置 Mapper。
     */
    private final NamespaceConfigMapper namespaceConfigMapper;

    /**
     * {@inheritDoc}
     *
     * <p>仅取主键最大的编辑态记录，使用单条分页查询，避免为配置装配触发无用的总数统计。
     */
    @Override
    public NamespaceConfigDO getEditingConfig(Long namespaceId) {
        return selectFirst(namespaceConfigMapper, new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.EDITING.getCode())
                .orderByDesc(NamespaceConfigDO::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<NamespaceConfigDO> listEditingConfigs(List<Long> namespaceIdList) {
        if (namespaceIdList == null || namespaceIdList.isEmpty()) {
            return List.of();
        }
        return namespaceConfigMapper.selectList(new LambdaQueryWrapper<NamespaceConfigDO>()
                .in(NamespaceConfigDO::getNamespaceId, namespaceIdList)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.EDITING.getCode())
                .orderByDesc(NamespaceConfigDO::getId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>每个 Namespace 与目标版本组成一对 OR 条件，再整体约束为发布态；
     * 不能拆成两个独立 IN 条件，否则回滚后可能读取其他 Namespace 所需版本的历史配置。
     */
    @Override
    public List<NamespaceConfigDO> listPublishedConfigsByVersions(Map<Long, Integer> namespaceVersions) {
        if (namespaceVersions == null || namespaceVersions.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<Long, Integer>> versions = namespaceVersions.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .toList();
        if (versions.isEmpty()) {
            return List.of();
        }
        return namespaceConfigMapper.selectList(new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.PUBLISHED.getCode())
                .and(query -> versions.forEach(entry -> query.or(pair -> pair
                        .eq(NamespaceConfigDO::getNamespaceId, entry.getKey())
                        .eq(NamespaceConfigDO::getVersion, entry.getValue()))))
                .orderByDesc(NamespaceConfigDO::getId));
    }

    @Override
    public NamespaceConfigDO getByNamespaceIdAndVersion(Long namespaceId, Integer version) {
        return selectFirst(namespaceConfigMapper, new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getVersion, version)
                .orderByDesc(NamespaceConfigDO::getId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>未发布时直接返回 null；有效版本必须同时匹配 Namespace、版本号与发布态，不能误取草稿。
     */
    @Override
    public NamespaceConfigDO getPublishedConfig(Long namespaceId, Integer version) {
        if (version == null || version <= 0) {
            return null;
        }

        return selectFirst(namespaceConfigMapper, new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getVersion, version)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.PUBLISHED.getCode())
                .orderByDesc(NamespaceConfigDO::getId));
    }

    @Override
    public List<NamespaceConfigDO> listPublishedConfigs(Long namespaceId) {
        return namespaceConfigMapper.selectList(new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.PUBLISHED.getCode())
                .orderByDesc(NamespaceConfigDO::getVersion));
    }

    @Override
    public int getMaxPublishedVersion(Long namespaceId) {
        NamespaceConfigDO latest = selectFirst(namespaceConfigMapper, new LambdaQueryWrapper<NamespaceConfigDO>()
                .select(NamespaceConfigDO::getVersion)
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.PUBLISHED.getCode())
                .orderByDesc(NamespaceConfigDO::getVersion));
        return latest == null ? 0 : latest.getVersion();
    }

    @Override
    public NamespaceConfigDO create(NamespaceConfigDO namespaceConfig) {
        namespaceConfigMapper.insert(namespaceConfig);
        return namespaceConfig;
    }

    @Override
    public void update(NamespaceConfigDO namespaceConfig) {
        namespaceConfigMapper.updateById(namespaceConfig);
    }
}
