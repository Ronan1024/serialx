package com.ronan.serialx.sdk.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.ronan.serialx.sdk.model.EndpointHealth;
import org.junit.jupiter.api.Test;

/**
 * 节点注册表测试。
 */
class EndpointRegistryTest {

    @Test
    void shouldAvoidExcludedEndpointWhenMultipleEndpointsExist() {
        EndpointRegistry registry = new EndpointRegistry(List.of("http://node-1", "http://node-2"), 3, Duration.ZERO);

        String selected = registry.select(Set.of("http://node-1"));

        assertEquals("http://node-2", selected);
    }

    @Test
    void shouldMarkEndpointUnhealthyWhenFailureThresholdReached() {
        EndpointRegistry registry = new EndpointRegistry(List.of("http://node-1", "http://node-2"), 1, Duration.ofSeconds(30));

        registry.markFailure("http://node-1");

        assertEquals(EndpointHealth.UNHEALTHY, registry.snapshots().stream()
                .filter(status -> status.getUrl().equals("http://node-1"))
                .findFirst()
                .orElseThrow()
                .getStatus());
        assertNotEquals("http://node-1", registry.select(Set.of()));
    }
}
