package com.ronan.serialx.standalone.config;

import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.ronan.serialx.common.node.NodeAllocationGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LocalNodeAllocationConfigTest {
    @Test void standaloneUsesTheSameManagerWithoutHttp() {
        Class<?> configuration = assertDoesNotThrow(() -> Class.forName(
                "com.ronan.serialx.standalone.config.LocalNodeAllocationConfig"));
        var manager = mock(SnowflakeNodeManager.class);
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=standalone")
                .withBean(SnowflakeNodeManager.class, () -> manager)
                .withUserConfiguration(configuration)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertSame(manager, context.getBean(NodeAllocationGateway.class));
                    assertTrue(context.getBeansOfType(NodeAllocationGateway.class).values().stream()
                            .allMatch(candidate -> candidate == manager));
                });
    }
}
