package com.ronan.serialx.infra.mapper;

import com.ronan.serialx.infra.entity.SnowflakeNodeDO;
import org.apache.ibatis.annotations.*;

/** 节点池的主库持久化操作；加锁和写入必须处于同一事务，先获取池锁再访问业务行。 */
@Mapper
public interface SnowflakeNodeMapper {
    /** 锁定固定的 0/0 节点行作为池级互斥锁；该行仍参与分配，不能删除。 */
    @Select("SELECT * FROM sx_snowflake_node WHERE datacenter_id=0 AND worker_id=0 FOR UPDATE")
    SnowflakeNodeDO lockPool();

    /** 获取主库 Unix 毫秒时间；应在池锁取得后调用，避免锁等待导致判断时间过旧。 */
    @Select("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)")
    long currentTimeMillis();

    /**
     * 锁定一个可安全复用的节点；租约失效和历史预留窗口结束必须同时满足。
     *
     * @param now 取得池锁后的主库时间，Unix 毫秒
     * @return 已锁定节点，没有候选节点时为 null
     */
    @Select("""
            SELECT * FROM sx_snowflake_node
            WHERE (owner_runtime_id IS NULL OR lease_expire_at_ms <= #{now}) AND reserved_until_ms < #{now}
            ORDER BY reserved_until_ms, datacenter_id, worker_id LIMIT 1 FOR UPDATE
            """)
    SnowflakeNodeDO lockAvailable(@Param("now") long now);

    /** 按联合主键锁定节点；不存在时返回 null，所有权校验由调用方完成。 */
    @Select("SELECT * FROM sx_snowflake_node WHERE datacenter_id=#{datacenterId} AND worker_id=#{workerId} FOR UPDATE")
    SnowflakeNodeDO lockNode(@Param("datacenterId") int datacenterId, @Param("workerId") int workerId);

    /**
     * 写入新所有者；调用方必须先锁定并确认节点可分配，SQL 不重复判断资格。
     *
     * @param session 待插入的运行会话
     * @return 更新行数，正常应为 1；历史预留水位只增不减
     */
    @Update("""
            UPDATE sx_snowflake_node SET owner_runtime_id=#{runtimeId}, lease_expire_at_ms=#{leaseExpireAtMs},
              reserved_until_ms=GREATEST(reserved_until_ms, #{reservedUntilMs}), updated_at=CURRENT_TIMESTAMP(3)
            WHERE datacenter_id=#{datacenterId} AND worker_id=#{workerId}
            """)
    int assign(com.ronan.serialx.infra.entity.SnowflakeNodeSessionDO session);

    @Update("""
            UPDATE sx_snowflake_node SET lease_expire_at_ms=GREATEST(lease_expire_at_ms, #{leaseExpireAtMs}),
              reserved_until_ms=GREATEST(reserved_until_ms, #{reservedUntilMs}), updated_at=CURRENT_TIMESTAMP(3)
            WHERE datacenter_id=#{datacenterId} AND worker_id=#{workerId} AND owner_runtime_id=#{runtimeId}
            """)
    /** 仅延长匹配所有者的租约和水位；调用方须持锁校验未过期，更新行数应为 1。 */
    int renew(com.ronan.serialx.infra.entity.SnowflakeNodeSessionDO session);

    @Update("""
            UPDATE sx_snowflake_node SET owner_runtime_id=NULL, lease_expire_at_ms=0, updated_at=CURRENT_TIMESTAMP(3)
            WHERE datacenter_id=#{datacenterId} AND worker_id=#{workerId} AND owner_runtime_id=#{runtimeId}
            """)
    /** 清除匹配所有者的租约但保留历史水位；返回 0 表示节点不存在或所有者已变化。 */
    int release(com.ronan.serialx.infra.entity.SnowflakeNodeSessionDO session);
}
