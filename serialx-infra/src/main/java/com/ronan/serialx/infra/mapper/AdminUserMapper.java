package com.ronan.serialx.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ronan.serialx.infra.entity.AdminUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台管理用户 Mapper。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserDO> {
}
