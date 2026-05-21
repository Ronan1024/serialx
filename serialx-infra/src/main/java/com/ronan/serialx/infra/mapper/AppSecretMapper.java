package com.ronan.serialx.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ronan.serialx.infra.entity.AppSecretDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务调用方应用密钥 Mapper。
 */
@Mapper
public interface AppSecretMapper extends BaseMapper<AppSecretDO> {
}
