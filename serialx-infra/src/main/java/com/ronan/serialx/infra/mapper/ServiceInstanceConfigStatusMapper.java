package com.ronan.serialx.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Service 实例配置状态 Mapper。
 */
@Mapper
public interface ServiceInstanceConfigStatusMapper extends BaseMapper<ServiceInstanceConfigStatusDO> {
}
