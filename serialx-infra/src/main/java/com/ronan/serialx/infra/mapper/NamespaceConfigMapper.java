package com.ronan.serialx.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Namespace 配置 Mapper。
 */
@Mapper
public interface NamespaceConfigMapper extends BaseMapper<NamespaceConfigDO> {
}
