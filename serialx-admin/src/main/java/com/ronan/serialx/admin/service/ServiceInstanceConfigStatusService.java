package com.ronan.serialx.admin.service;

import java.util.List;

import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;

/**
 * Service 实例配置状态数据访问服务。
 */
public interface ServiceInstanceConfigStatusService {

    /**
     * 按 Namespace 编码查询实例状态。
     */
    List<ServiceInstanceConfigStatusDO> listByNamespaceCode(String namespaceCode);
}
