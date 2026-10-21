package com.ronan.serialx.domain.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.config.SnowflakeNodeProperties;
import com.ronan.serialx.infra.http.OutboundHttpProperties;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.config.SnowflakeGeneratorConfig;
import com.ronan.serialx.application.node.SnowflakeNodeLifecycle;
import static org.mockito.Mockito.*;
import com.ronan.serialx.domain.factory.NamespaceRuntimeFactory;
import com.ronan.serialx.domain.model.NamespaceRuntimeSnapshot;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import com.ronan.serialx.domain.runtime.SegmentNamespaceRuntime;
import com.ronan.serialx.domain.runtime.SnowflakeNamespaceRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * NamespaceRuntimeFactory 单元测试。
 */
class NamespaceRuntimeFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SnowflakeNodeProperties snowflakeNodeProperties = buildSnowflakeNodeProperties();

    private final NamespaceRuntimeFactory namespaceRuntimeFactory =
            new NamespaceRuntimeFactory(new SnowflakeGenerator(1, 1));

    @Test
    void shouldCreateSegmentRuntimeAsLoadedPlaceholder() throws Exception {
        NamespaceRuntimeSnapshot snapshot = NamespaceRuntimeSnapshot.builder()
                .namespaceId(1L)
                .namespaceCode("order_id")
                .version(1)
                .idMode(1)
                .config(objectMapper.readTree("""
                        {"stepSize":100,"initialValue":0,"cacheSegments":1,"maxValue":1000}
                        """))
                .build();

        NamespaceRuntime runtime = namespaceRuntimeFactory.create(snapshot);

        assertInstanceOf(SegmentNamespaceRuntime.class, runtime);
        assertEquals("order_id", runtime.getNamespaceCode());
        assertThrows(RuntimeException.class, runtime::nextId);
    }

    @Test
    void shouldCreateSnowflakeRuntime() throws Exception {
        NamespaceRuntimeSnapshot snapshot = NamespaceRuntimeSnapshot.builder()
                .namespaceId(2L)
                .namespaceCode("payment_id")
                .version(3)
                .idMode(2)
                .config(objectMapper.readTree("{}"))
                .build();

        NamespaceRuntime runtime = namespaceRuntimeFactory.create(snapshot);

        assertInstanceOf(SnowflakeNamespaceRuntime.class, runtime);
        assertTrue(runtime.nextId() > 0);
    }

    @Test
    void shouldShareSequenceAcrossNamespacesAndVersions() {
        var factory = new NamespaceRuntimeFactory(new SnowflakeGenerator(1, 1, () -> 1704067200001L, () -> {}));
        var order = factory.create(snapshot("order_id", 1));
        var payment = factory.create(snapshot("payment_id", 1));
        long first = order.nextId();
        assertEquals(first + 1, payment.nextId());
        assertEquals(first + 2, factory.create(snapshot("order_id", 2)).nextId());
    }

    @Test
    void shouldWireOneGeneratorAndFactoryPerApplication() {
        new ApplicationContextRunner()
                .withUserConfiguration(SnowflakeGeneratorConfig.class, NamespaceRuntimeFactory.class)
                .withBean(SnowflakeNodeLifecycle.class, () -> {
                    var lifecycle = mock(SnowflakeNodeLifecycle.class);
                    when(lifecycle.generator()).thenReturn(new SnowflakeGenerator(1, 1));
                    return lifecycle;
                })
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(SnowflakeGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(NamespaceRuntimeFactory.class).size());
                    assertTrue(context.getBean(NamespaceRuntimeFactory.class).create(snapshot("order_id", 1)).nextId() > 0);
                });
    }

    private NamespaceRuntimeSnapshot snapshot(String code, int version) {
        return NamespaceRuntimeSnapshot.builder().namespaceId(1L).namespaceCode(code).version(version).idMode(2).build();
    }

    private SnowflakeNodeProperties buildSnowflakeNodeProperties() {
        SnowflakeNodeProperties properties = new SnowflakeNodeProperties(new OutboundHttpProperties());
        return properties;
    }
}
