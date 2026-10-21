package com.ronan.serialx.infra.entity;

import lombok.Getter;
import lombok.Setter;

/** 节点组合以 datacenterId、workerId 为联合主键，水位永久保留。 */
@Getter
@Setter
public class SnowflakeNodeDO {
    /** 联合主键的高 5 位，范围 0～31。 */
    private int datacenterId;
    /** 联合主键的低 5 位，范围 0～31。 */
    private int workerId;
    /** 当前所有者的运行会话 UUID；释放后为 null。 */
    private String ownerRuntimeId;
    /** 主库时钟下的租约到期点，Unix 毫秒；释放后为 0。 */
    private long leaseExpireAtMs;
    /** 历任所有者已预留的最高时间，Unix 毫秒；释放或过期均不得清零、倒退。 */
    private long reservedUntilMs;
}
