package com.ronan.serialx.sdk.transport;

import java.time.Instant;

import com.ronan.serialx.sdk.model.EndpointHealth;
import com.ronan.serialx.sdk.model.EndpointStatus;

/**
 * SDK 内部维护的节点状态。
 */
class EndpointState {

    private final String url;

    private EndpointHealth status = EndpointHealth.HEALTHY;

    private int failureCount;

    private Instant lastSuccessAt;

    private Instant lastFailureAt;

    private Instant unhealthyUntil;

    private long lastLatencyMs = -1;

    EndpointState(String url) {
        this.url = url;
    }

    synchronized void markSuccess(long latencyMs) {
        status = EndpointHealth.HEALTHY;
        failureCount = 0;
        lastSuccessAt = Instant.now();
        unhealthyUntil = null;
        lastLatencyMs = latencyMs;
    }

    synchronized void markFailure(int failureThreshold, java.time.Duration unhealthyCooldown) {
        failureCount++;
        lastFailureAt = Instant.now();
        if (failureCount >= failureThreshold) {
            status = EndpointHealth.UNHEALTHY;
            unhealthyUntil = Instant.now().plus(unhealthyCooldown);
        } else {
            status = EndpointHealth.SUSPECT;
        }
    }

    synchronized boolean isSelectable(Instant now) {
        if (status == EndpointHealth.HEALTHY) {
            return true;
        }
        return unhealthyUntil == null || !now.isBefore(unhealthyUntil);
    }

    synchronized boolean needsHealthCheck(Instant now) {
        return status != EndpointHealth.HEALTHY && isSelectable(now);
    }

    synchronized EndpointStatus snapshot() {
        return new EndpointStatus(url, status, failureCount, lastSuccessAt, lastFailureAt, unhealthyUntil, lastLatencyMs);
    }

    String getUrl() {
        return url;
    }
}
