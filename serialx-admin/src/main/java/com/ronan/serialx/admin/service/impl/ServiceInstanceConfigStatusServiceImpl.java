package com.ronan.serialx.admin.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import com.ronan.serialx.infra.mapper.ServiceInstanceConfigStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service 实例配置状态数据访问服务实现。
 */
@Service
@RequiredArgsConstructor
public class ServiceInstanceConfigStatusServiceImpl implements ServiceInstanceConfigStatusService {

    /**
     * Service 实例配置状态 Mapper。
     */
    private final ServiceInstanceConfigStatusMapper serviceInstanceConfigStatusMapper;

    @Override
    public List<ServiceInstanceConfigStatusDO> listByNamespaceCode(String namespaceCode) {
        return serviceInstanceConfigStatusMapper.selectList(new LambdaQueryWrapper<ServiceInstanceConfigStatusDO>()
                .eq(ServiceInstanceConfigStatusDO::getNamespaceCode, namespaceCode)
                .orderByDesc(ServiceInstanceConfigStatusDO::getLastReportTime));
    }
}
