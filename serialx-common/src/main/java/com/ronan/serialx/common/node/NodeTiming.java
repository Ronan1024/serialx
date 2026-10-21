package com.ronan.serialx.common.node;

import java.time.Duration;

/**
 * 控制面与数据面共享的节点时间策略，不接受外部配置覆盖。
 */
public final class NodeTiming {

    public static final Duration LEASE_DURATION = Duration.ofSeconds(60);

    public static final Duration SAFETY_MARGIN = Duration.ofSeconds(3);

    public static final long RENEW_INTERVAL_SECONDS = 10;

    public static final long HEARTBEAT_INTERVAL_SECONDS = 20;

    // 允许连续错过两个心跳周期；仅用于在线观测，不授予发号权限。
    public static final Duration HEARTBEAT_TTL = Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS).multipliedBy(3);

    private NodeTiming() {
    }
}
