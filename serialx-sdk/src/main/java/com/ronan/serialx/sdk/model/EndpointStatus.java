package com.ronan.serialx.sdk.model;

import java.time.Instant;

/**
 * SerialX service 节点状态快照。
 */
public class EndpointStatus {

    private final String url;

    private final EndpointHealth status;

    private final int failureCount;

    private final Instant lastSuccessAt;

    private final Instant lastFailureAt;

    private final Instant unhealthyUntil;

    private final long lastLatencyMs;

    public EndpointStatus(
            String url,
            EndpointHealth status,
            int failureCount,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            Instant unhealthyUntil,
            long lastLatencyMs) {
        this.url = url;
        this.status = status;
        this.failureCount = failureCount;
        this.lastSuccessAt = lastSuccessAt;
        this.lastFailureAt = lastFailureAt;
        this.unhealthyUntil = unhealthyUntil;
        this.lastLatencyMs = lastLatencyMs;
    }

    public String getUrl() {
        return url;
    }

    public EndpointHealth getStatus() {
        return status;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public Instant getUnhealthyUntil() {
        return unhealthyUntil;
    }

    public long getLastLatencyMs() {
        return lastLatencyMs;
    }
}
