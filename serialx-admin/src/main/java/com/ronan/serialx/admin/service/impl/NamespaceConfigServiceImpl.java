package com.ronan.serialx.admin.service.impl;

import static com.ronan.serialx.infra.support.MybatisQueryUtils.selectFirst;

import java.util.List;

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

    @Override
    public NamespaceConfigDO getEditingConfig(Long namespaceId) {
        return selectFirst(namespaceConfigMapper, new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.EDITING.getCode())
                .orderByDesc(NamespaceConfigDO::getId));
    }

    @Override
    public NamespaceConfigDO getByNamespaceIdAndVersion(Long namespaceId, Integer version) {
        return selectFirst(namespaceConfigMapper, new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getVersion, version)
                .orderByDesc(NamespaceConfigDO::getId));
    }

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
    public NamespaceConfigDO create(NamespaceConfigDO namespaceConfig) {
        namespaceConfigMapper.insert(namespaceConfig);
        return namespaceConfig;
    }

    @Override
    public void update(NamespaceConfigDO namespaceConfig) {
        namespaceConfigMapper.updateById(namespaceConfig);
    }
}
