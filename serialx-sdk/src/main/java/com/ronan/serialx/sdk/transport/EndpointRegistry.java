package com.ronan.serialx.sdk.transport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.ronan.serialx.sdk.model.EndpointStatus;

/**
 * SerialX service 节点注册表。
 */
public class EndpointRegistry {

    private final List<EndpointState> endpoints;

    private final AtomicInteger cursor = new AtomicInteger();

    private final int failureThreshold;

    private final Duration unhealthyCooldown;

    public EndpointRegistry(List<String> baseUrls, int failureThreshold, Duration unhealthyCooldown) {
        this.endpoints = new CopyOnWriteArrayList<>(baseUrls.stream().map(EndpointState::new).toList());
        this.failureThreshold = failureThreshold;
        this.unhealthyCooldown = unhealthyCooldown;
    }

    public String select(Set<String> excluded) {
        Set<String> safeExcluded = excluded == null ? Set.of() : excluded;
        Instant now = Instant.now();
        // 首选仍在可用窗口内的节点，并排除当前调用已经尝试失败的 endpoint。
        Optional<String> normal = selectFrom(selectableEndpoints(now, safeExcluded));
        if (normal.isPresent()) {
            return normal.get();
        }
        if (endpoints.size() == 1) {
            return endpoints.get(0).getUrl();
        }
        // 所有节点都不健康时，允许冷却期结束的节点半开探测，避免故障恢复后长期不可用。
        Optional<String> halfOpen = selectFrom(endpoints.stream()
                .filter(endpoint -> !safeExcluded.contains(endpoint.getUrl()))
                .filter(endpoint -> endpoint.isSelectable(now))
                .toList());
        return halfOpen.orElse(null);
    }

    public List<String> endpointsNeedingHealthCheck() {
        Instant now = Instant.now();
        return endpoints.stream()
                .filter(endpoint -> endpoint.needsHealthCheck(now))
                .map(EndpointState::getUrl)
                .toList();
    }

    public void markSuccess(String url, long latencyMs) {
        find(url).ifPresent(endpoint -> endpoint.markSuccess(latencyMs));
    }

    public void markFailure(String url) {
        find(url).ifPresent(endpoint -> endpoint.markFailure(failureThreshold, unhealthyCooldown));
    }

    public List<EndpointStatus> snapshots() {
        return endpoints.stream()
                .map(EndpointState::snapshot)
                .sorted(Comparator.comparing(EndpointStatus::getUrl))
                .toList();
    }

    public Set<String> allEndpointUrls() {
        Set<String> urls = new HashSet<>();
        endpoints.forEach(endpoint -> urls.add(endpoint.getUrl()));
        return urls;
    }

    private List<EndpointState> selectableEndpoints(Instant now, Set<String> excluded) {
        return endpoints.stream()
                .filter(endpoint -> !excluded.contains(endpoint.getUrl()))
                .filter(endpoint -> endpoint.isSelectable(now))
                .toList();
    }

    private Optional<String> selectFrom(List<EndpointState> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<EndpointState> copy = new ArrayList<>(candidates);
        // 简单轮询避免长期固定命中第一个健康节点，足够覆盖 SDK 侧轻量负载分摊。
        int index = Math.floorMod(cursor.getAndIncrement(), copy.size());
        return Optional.of(copy.get(index).getUrl());
    }

    private Optional<EndpointState> find(String url) {
        return endpoints.stream().filter(endpoint -> endpoint.getUrl().equals(url)).findFirst();
    }
}
