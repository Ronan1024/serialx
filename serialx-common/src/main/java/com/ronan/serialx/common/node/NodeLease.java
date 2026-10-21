package com.ronan.serialx.common.node;

import lombok.Data;

/**
 * 控制面已提交的节点身份、租约与发号时间窗口；所有绝对时间均为 Unix 毫秒。
 * 租约代表所有权期限，时间窗口代表可编码的时间范围，两者不能互相替代。
 */
@Data
public class NodeLease {

    /**
     * 本次进程启动的唯一标识，不能跨重启复用。
     */
    private String runtimeId;
    /**
     * 节点编号的高 5 位，不要求对应真实地理数据中心。
     */
    private int datacenterId;
    /**
     * 节点编号的低 5 位，与 datacenterId 共同标识节点。
     */
    private int workerId;
    /**
     * 可发号时间下界，包含该毫秒，严格大于上任会话的预留上界。
     */
    private long validFromMs;
    /**
     * 已持久化的可发号时间上界，包含该毫秒。
     */
    private long reservedUntilMs;
    /**
     * 主库时钟下的租约到期点，到达该点即失效。
     */
    private long leaseExpireAtMs;
    /**
     * 主库采样时刻的剩余租期，不是初始租期；调用方还需扣除请求耗时。
     */
    private long leaseDurationMs;
    /**
     * 固定雪花纪元的 Unix 毫秒值。
     */
    private long epoch;
    /**
     * 工作节点位数
     */
    private int workerIdBits;
    /**
     * 数据中心位数
     */
    private int datacenterIdBits;
    /**
     * 每毫秒序列位数
     */
    private int sequenceBits;
}
