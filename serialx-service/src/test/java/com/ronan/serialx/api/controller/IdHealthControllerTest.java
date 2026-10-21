package com.ronan.serialx.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ronan.serialx.api.dto.IdHealthResponse;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import com.ronan.serialx.application.node.SnowflakeNodeLifecycle;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

/**
 * 发号健康接口测试。
 */
class IdHealthControllerTest {

    @Test
    void shouldReturnCurrentServiceHealth() {
        NamespaceRegistry namespaceRegistry = new NamespaceRegistry();
        NamespaceRuntime runtime = mock(NamespaceRuntime.class);
        when(runtime.isAvailable()).thenReturn(true);
        namespaceRegistry.replace("order_id", runtime);
        ServiceInstanceIdentity serviceInstanceIdentity = mock(ServiceInstanceIdentity.class);
        when(serviceInstanceIdentity.getInstanceId()).thenReturn("node-1");
        var lifecycle = mock(SnowflakeNodeLifecycle.class);
        when(lifecycle.isAvailable()).thenReturn(true);
        IdHealthController controller = new IdHealthController(namespaceRegistry, serviceInstanceIdentity, lifecycle);

        IdHealthResponse response = controller.health().getBody();

        assertTrue(response.isAvailable());
        assertEquals("node-1", response.getNodeId());
        assertEquals(1, response.getLoadedNamespaceCount());
        when(lifecycle.isAvailable()).thenReturn(false);
        assertEquals(503, controller.health().getStatusCode().value());
        assertFalse(controller.health().getBody().isAvailable());
        when(lifecycle.isAvailable()).thenReturn(true);
        when(runtime.isAvailable()).thenReturn(false);
        assertEquals(503, controller.health().getStatusCode().value());
    }
}
