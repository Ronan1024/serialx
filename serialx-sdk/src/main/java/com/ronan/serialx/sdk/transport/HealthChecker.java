package com.ronan.serialx.sdk.transport;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ronan.serialx.sdk.config.SerialxClientConfig;

/**
 * 后台健康检查，仅用于恢复被动降级的节点。
 */
public class HealthChecker implements AutoCloseable {

    private final EndpointRegistry endpointRegistry;

    private final SerialxHttpClient httpClient;

    private final SerialxClientConfig config;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "serialx-health-checker");
                thread.setDaemon(true);
                return thread;
            });

    public HealthChecker(EndpointRegistry endpointRegistry, SerialxHttpClient httpClient, SerialxClientConfig config) {
        this.endpointRegistry = endpointRegistry;
        this.httpClient = httpClient;
        this.config = config;
    }

    public void start() {
        long intervalMillis = config.getHealthCheckInterval().toMillis();
        executor.scheduleWithFixedDelay(this::check, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void check() {
        if (closed.get()) {
            return;
        }
        // 主动健康检查只探测非健康节点，用于恢复流量，不参与正常请求的实时决策。
        for (String endpoint : endpointRegistry.endpointsNeedingHealthCheck()) {
            long start = System.nanoTime();
            if (httpClient.health(endpoint)) {
                endpointRegistry.markSuccess(endpoint, elapsedMillis(start));
            } else {
                endpointRegistry.markFailure(endpoint);
            }
        }
    }

    private long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
