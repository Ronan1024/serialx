package com.ronan.serialx.infra.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 一次进程启动对应的持久化会话，以 runtimeId 为主键。
 * 保留终态记录用于幂等校验，不能通过再次注册复活历史会话。
 */
@Getter
@Setter
public class SnowflakeNodeSessionDO {
    /** 每次进程启动独立生成的 UUID，同时作为注册幂等键。 */
    private String runtimeId;
    /** 实例展示标识；同一 runtimeId 的注册重试必须保持一致。 */
    private String instanceId;
    /** 本会话分配到的节点高 5 位。 */
    private int datacenterId;
    /** 本会话分配到的节点低 5 位。 */
    private int workerId;
    /** 本会话可发号时间下界，Unix 毫秒，包含边界且不会在续租时变化。 */
    private long validFromMs;
    /** 本会话已确认的发号时间上界，Unix 毫秒，包含边界且只能递增。 */
    private long reservedUntilMs;
    /** 主库时钟下的租约到期点，Unix 毫秒，续租不能使其倒退。 */
    private long leaseExpireAtMs;
    /** ACTIVE 为活动态，EXPIRED 和 RELEASED 为不可复活的终态。 */
    private String status;
}
