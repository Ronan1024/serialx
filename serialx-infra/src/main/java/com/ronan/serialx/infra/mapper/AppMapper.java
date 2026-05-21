package com.ronan.serialx.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ronan.serialx.infra.entity.AppDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务调用方应用 Mapper。
 */
@Mapper
public interface AppMapper extends BaseMapper<AppDO> {
}
