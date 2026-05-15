package com.ronan.serialx.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.admin.service.NamespaceService;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.mapper.NamespaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * Namespace 数据访问服务实现。
 */
@Service
@RequiredArgsConstructor
public class NamespaceServiceImpl implements NamespaceService {

    /**
     * Namespace Mapper。
     */
    private final NamespaceMapper namespaceMapper;

    @Override
    public NamespaceDO getById(Long id) {
        return namespaceMapper.selectOne(new LambdaQueryWrapper<NamespaceDO>()
                .eq(NamespaceDO::getId, id)
                .eq(NamespaceDO::getDeleted, 0));
    }

    @Override
    public NamespaceDO getByCode(String namespaceCode) {
        return namespaceMapper.selectOne(new LambdaQueryWrapper<NamespaceDO>()
                .eq(NamespaceDO::getNamespaceCode, namespaceCode)
                .eq(NamespaceDO::getDeleted, 0));
    }

    @Override
    public List<NamespaceDO> listByCodes(Collection<String> namespaceCodes) {
        if (namespaceCodes == null || namespaceCodes.isEmpty()) {
            return List.of();
        }
        return namespaceMapper.selectList(new LambdaQueryWrapper<NamespaceDO>()
                .in(NamespaceDO::getNamespaceCode, namespaceCodes)
                .eq(NamespaceDO::getDeleted, 0));
    }

    @Override
    public boolean existsByCode(String namespaceCode) {
        return namespaceMapper.selectCount(new LambdaQueryWrapper<NamespaceDO>()
                .eq(NamespaceDO::getNamespaceCode, namespaceCode)
                .eq(NamespaceDO::getDeleted, 0)) > 0;
    }

    @Override
    public Page<NamespaceDO> pageNamespaces(long pageNo, long pageSize, String keyword, Integer status, Integer idMode) {
        LambdaQueryWrapper<NamespaceDO> wrapper = new LambdaQueryWrapper<NamespaceDO>()
                .eq(NamespaceDO::getDeleted, 0)
                .eq(status != null, NamespaceDO::getStatus, status)
                .eq(idMode != null, NamespaceDO::getIdMode, idMode)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(NamespaceDO::getNamespaceCode, keyword)
                        .or()
                        .like(NamespaceDO::getNamespaceName, keyword)
                        .or()
                        .like(NamespaceDO::getOwner, keyword))
                .orderByDesc(NamespaceDO::getId);
        return namespaceMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
    }

    @Override
    public NamespaceDO create(NamespaceDO namespace) {
        namespaceMapper.insert(namespace);
        return namespace;
    }

    @Override
    public void update(NamespaceDO namespace) {
        namespaceMapper.updateById(namespace);
    }

    @Override
    public void markDeleted(Long id) {
        NamespaceDO namespace = new NamespaceDO();
        namespace.setId(id);
        namespace.setDeleted(1);
        namespaceMapper.updateById(namespace);
    }
}
