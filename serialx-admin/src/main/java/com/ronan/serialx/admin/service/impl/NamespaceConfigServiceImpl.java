package com.ronan.serialx.admin.service.impl;

import java.time.LocalDateTime;
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
        return namespaceConfigMapper.selectOne(new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.EDITING.getCode())
                .orderByDesc(NamespaceConfigDO::getId)
                .last("limit 1"));
    }

    @Override
    public NamespaceConfigDO getByNamespaceIdAndVersion(Long namespaceId, Integer version) {
        return namespaceConfigMapper.selectOne(new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getVersion, version)
                .last("limit 1"));
    }

    @Override
    public NamespaceConfigDO getPublishedConfig(Long namespaceId, Integer version) {
        return namespaceConfigMapper.selectOne(new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespaceId)
                .eq(NamespaceConfigDO::getVersion, version)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.PUBLISHED.getCode())
                .last("limit 1"));
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
        LocalDateTime now = LocalDateTime.now();
        namespaceConfig.setCreatedAt(now);
        namespaceConfig.setUpdatedAt(now);
        namespaceConfigMapper.insert(namespaceConfig);
        return namespaceConfig;
    }

    @Override
    public void update(NamespaceConfigDO namespaceConfig) {
        namespaceConfig.setUpdatedAt(LocalDateTime.now());
        namespaceConfigMapper.updateById(namespaceConfig);
    }
}
