package com.ronan.serialx.infra.mapper;

import com.ronan.serialx.infra.entity.SnowflakeNodeSessionDO;
import org.apache.ibatis.annotations.*;

/** 会话持久化入口；调用方须在池锁保护下执行状态机校验。 */
@Mapper
public interface SnowflakeNodeSessionMapper {
    /** 按注册幂等键锁定会话，不存在时返回 null；必须在事务内调用。 */
    @Select("SELECT * FROM sx_snowflake_node_session WHERE runtime_id=#{runtimeId} FOR UPDATE")
    SnowflakeNodeSessionDO lockSession(@Param("runtimeId") String runtimeId);

    /** 插入新会话并返回行数；runtimeId 唯一约束保护幂等键，重复键抛出异常。 */
    @Insert("""
            INSERT INTO sx_snowflake_node_session
              (runtime_id, instance_id, datacenter_id, worker_id, valid_from_ms, reserved_until_ms, lease_expire_at_ms, status)
            VALUES (#{runtimeId}, #{instanceId}, #{datacenterId}, #{workerId}, #{validFromMs}, #{reservedUntilMs}, #{leaseExpireAtMs}, #{status})
            """)
    int insert(SnowflakeNodeSessionDO session);

    /** 更新水位、租期和状态，不改变首次节点与时间下界；时间只增不减，终态不可复活由调用方保证。 */
    @Update("""
            UPDATE sx_snowflake_node_session SET reserved_until_ms=GREATEST(reserved_until_ms, #{reservedUntilMs}),
              lease_expire_at_ms=GREATEST(lease_expire_at_ms, #{leaseExpireAtMs}), status=#{status}, updated_at=CURRENT_TIMESTAMP(3)
            WHERE runtime_id=#{runtimeId}
            """)
    int update(SnowflakeNodeSessionDO session);
}
