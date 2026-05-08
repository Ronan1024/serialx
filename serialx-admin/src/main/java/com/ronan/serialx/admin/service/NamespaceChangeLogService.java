package com.ronan.serialx.admin.service;

import java.util.List;

import com.ronan.serialx.infra.entity.NamespaceChangeLogDO;

/**
 * Namespace 变更日志数据访问服务。
 */
public interface NamespaceChangeLogService {

    /**
     * 新增变更日志。
     */
    void create(NamespaceChangeLogDO changeLog);

    /**
     * 查询指定 Namespace 变更日志。
     */
    List<NamespaceChangeLogDO> listByNamespaceId(Long namespaceId);
}
