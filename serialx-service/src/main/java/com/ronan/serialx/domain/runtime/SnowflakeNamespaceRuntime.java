package com.ronan.serialx.domain.runtime;

/**
 * Snowflake 模式本地运行时。
 */
public class SnowflakeNamespaceRuntime implements NamespaceRuntime {

    /**
     * 默认起始时间戳，2024-01-01 00:00:00 UTC。
     */
    private static final long DEFAULT_EPOCH = 1704067200000L;

    private final String namespaceCode;

    private final Integer version;

    private final Integer idMode;

    private final long workerId;

    private final long datacenterId;

    private final long workerIdShift;

    private final long datacenterIdShift;

    private final long timestampLeftShift;

    private final long sequenceMask;

    private long sequence;

    private long lastTimestamp = -1L;

    public SnowflakeNamespaceRuntime(String namespaceCode, Integer version, Integer idMode, long workerId, long datacenterId, long workerIdBits, long datacenterIdBits, long sequenceBits) {
        this.namespaceCode = namespaceCode;
        this.version = version;
        this.idMode = idMode;
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.workerIdShift = sequenceBits;
        this.datacenterIdShift = sequenceBits + workerIdBits;
        this.timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
        this.sequenceMask = ~(-1L << sequenceBits);
    }

    @Override
    public String getNamespaceCode() {
        return namespaceCode;
    }

    @Override
    public Integer getVersion() {
        return version;
    }

    @Override
    public Integer getIdMode() {
        return idMode;
    }

    @Override
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("clock moved backwards, namespaceCode=" + namespaceCode);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - DEFAULT_EPOCH) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    private long waitNextMillis(long lastTimestampValue) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestampValue) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
