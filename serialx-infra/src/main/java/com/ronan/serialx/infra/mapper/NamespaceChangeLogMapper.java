package com.ronan.serialx.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ronan.serialx.infra.entity.NamespaceChangeLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Namespace 变更日志 Mapper。
 */
@Mapper
public interface NamespaceChangeLogMapper extends BaseMapper<NamespaceChangeLogDO> {
}
