package com.ronan.serialx.core.snowflake;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 实例级雪花生成器；节点身份和安全检查由调用方提供。
 * 固定布局为 41 位相对毫秒时间、5 位数据中心、5 位工作节点和 12 位序列。
 * 布局常量同时约束租约兼容性，不允许按 Namespace 或部署配置覆盖。
 */
public final class SnowflakeGenerator {
    public static final long EPOCH = 1704067200000L;
    public static final int TIMESTAMP_BITS = 41;
    public static final int DATACENTER_ID_BITS = 5;
    public static final int WORKER_ID_BITS = 5;
    public static final int SEQUENCE_BITS = 12;

    // 节点坐标与可编码时间的上界均包含边界。
    public static final long MAX_DATACENTER_ID = (1L << DATACENTER_ID_BITS) - 1;
    public static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    public static final long MAX_TIMESTAMP = EPOCH + (1L << TIMESTAMP_BITS) - 1;

    private static final int DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final int TIMESTAMP_SHIFT = DATACENTER_ID_SHIFT + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_WAIT_NANOS = 1_000_000_000L;

    private final long nodeBits;
    private final LongSupplier clock;
    private final Runnable safetyCheck;
    private long sequence;
    private long lastTimestamp = -1;

    public SnowflakeGenerator(long workerId, long datacenterId) {
        this(workerId, datacenterId, System::currentTimeMillis, () -> {
        });
    }

    public SnowflakeGenerator(long workerId, long datacenterId, LongSupplier clock, Runnable safetyCheck) {
        if (workerId < 0 || workerId > MAX_WORKER_ID || datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("雪花 workerId 和 datacenterId 必须在 0—31 之间");
        }
        this.nodeBits = (datacenterId << DATACENTER_ID_SHIFT) | (workerId << SEQUENCE_BITS);
        this.clock = Objects.requireNonNull(clock);
        this.safetyCheck = Objects.requireNonNull(safetyCheck);
    }

    /**
     * 不消费序列的安全状态探测。
     */
    public synchronized boolean isAvailable() {
        try {
            safetyCheck.run();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public synchronized long nextId() {
        long timestamp = readTimestamp();
        long nextSequence = timestamp == lastTimestamp ? sequence + 1 : 0;
        if (nextSequence > SEQUENCE_MASK) {
            long started = System.nanoTime();
            do {
                if (System.nanoTime() - started >= MAX_WAIT_NANOS || Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("雪花序列耗尽，等待时间前进失败");
                }
                Thread.onSpinWait();
                timestamp = readTimestamp();
            } while (timestamp == lastTimestamp);
            nextSequence = 0;
        }
        // 检查失败时不提交时间和序列，避免下一次调用重复消费序列。
        safetyCheck.run();
        lastTimestamp = timestamp;
        sequence = nextSequence;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | nodeBits | sequence;
    }

    private long readTimestamp() {
        safetyCheck.run();
        long timestamp = clock.getAsLong();
        if (timestamp < EPOCH || timestamp > MAX_TIMESTAMP) {
            throw new IllegalStateException("雪花时间超出 41 位有效范围");
        }
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("雪花时钟发生回拨");
        }
        return timestamp;
    }
}
