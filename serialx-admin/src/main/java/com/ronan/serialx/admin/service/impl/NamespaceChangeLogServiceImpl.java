package com.ronan.serialx.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ronan.serialx.admin.service.NamespaceChangeLogService;
import com.ronan.serialx.infra.entity.NamespaceChangeLogDO;
import com.ronan.serialx.infra.mapper.NamespaceChangeLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Namespace 变更日志数据访问服务实现。
 */
@Service
@RequiredArgsConstructor
public class NamespaceChangeLogServiceImpl implements NamespaceChangeLogService {

    /**
     * Namespace 变更日志 Mapper。
     */
    private final NamespaceChangeLogMapper namespaceChangeLogMapper;

    @Override
    public void create(NamespaceChangeLogDO changeLog) {
        changeLog.setCreatedAt(LocalDateTime.now());
        namespaceChangeLogMapper.insert(changeLog);
    }

    @Override
    public List<NamespaceChangeLogDO> listByNamespaceId(Long namespaceId) {
        return namespaceChangeLogMapper.selectList(new LambdaQueryWrapper<NamespaceChangeLogDO>()
                .eq(NamespaceChangeLogDO::getNamespaceId, namespaceId)
                .orderByDesc(NamespaceChangeLogDO::getId));
    }
}
