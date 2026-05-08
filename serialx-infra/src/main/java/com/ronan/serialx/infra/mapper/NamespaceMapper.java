package com.ronan.serialx.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ronan.serialx.infra.entity.NamespaceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Namespace Mapper。
 */
@Mapper
public interface NamespaceMapper extends BaseMapper<NamespaceDO> {
}
