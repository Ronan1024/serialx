package com.ronan.serialx.application.refresh;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.adapter.redis.ServiceInstanceStatusWriterImpl;
import com.ronan.serialx.common.model.NamespaceChangeMessage;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.domain.factory.NamespaceRuntimeFactory;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.mapper.NamespaceConfigMapper;
import com.ronan.serialx.infra.mapper.NamespaceMapper;
import com.ronan.serialx.subscriber.NamespaceChangeSubscriber;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import com.ronan.serialx.support.LogCapture;
import ch.qos.logback.classic.Level;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NamespaceRuntimeLoaderTest {
    private NamespaceMapper namespaces;
    private NamespaceConfigMapper configs;
    private NamespaceRegistry registry;
    private NamespaceRuntimeLoader loader;
    private NamespaceChangeSubscriber subscriber;
    private ServiceInstanceStatusWriterImpl statusWriter;
    private final AtomicReference<NamespaceDO> current = new AtomicReference<>();
    private final AtomicInteger configReads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        var metadata = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(metadata, NamespaceDO.class);
        TableInfoHelper.initTableInfo(metadata, NamespaceConfigDO.class);
        namespaces = mock(NamespaceMapper.class);
        configs = mock(NamespaceConfigMapper.class);
        registry = new NamespaceRegistry();
        statusWriter = mock(ServiceInstanceStatusWriterImpl.class);
        var properties = new ServiceInstanceProperties();
        properties.setInstanceId("svc-1");
        var identity = new ServiceInstanceIdentity(properties, new MockEnvironment().withProperty("server.port", "8080"));
        loader = new NamespaceRuntimeLoader(namespaces, configs,
                new NamespaceRuntimeFactory(new SnowflakeGenerator(1, 1, () -> 1704067200001L, () -> {})),
                registry, statusWriter, properties, identity, new ObjectMapper());
        subscriber = new NamespaceChangeSubscriber(new ObjectMapper(), loader, properties);
        current.set(namespace(2, 1));
        when(namespaces.selectPage(any(), any())).thenAnswer(invocation -> page(current.get()));
        when(namespaces.selectList(any())).thenAnswer(invocation -> current.get() == null ? List.of() : List.of(current.get()));
        when(configs.selectPage(any(), any())).thenAnswer(invocation -> {
            configReads.incrementAndGet();
            LambdaQueryWrapper<NamespaceConfigDO> query = invocation.getArgument(1);
            query.getSqlSegment();
            int version = (Integer) query.getParamNameValuePairs().get("MPGENVAL2");
            return page(NamespaceConfigDO.builder().namespaceId(1L).version(version).idMode(2)
                    .configJson("{}").publishStatus(1).build());
        });
    }

    @Test
    void shouldLoadPublishedEnabledNamespace() {
        loader.loadPublishedNamespaces();
        assertEquals(2, registry.get("order_id").orElseThrow().getVersion());
        verify(statusWriter).saveOrUpdate(any());
    }

    @Test void logsLoadChangesAtInfoAndUnchangedVersionsAtDebug() {
        try (var logs = new LogCapture(NamespaceRuntimeLoader.class, Level.DEBUG)) {
            loader.loadPublishedNamespaces();
            loader.loadPublishedNamespaces();
            assertTrue(logs.contains(Level.INFO, "Namespace 运行时加载完成"));
            assertTrue(logs.contains(Level.DEBUG, "Namespace 全量加载完成"));
            assertTrue(logs.contains(Level.DEBUG, "Namespace 版本未变化"));
            assertTrue(logs.contains(Level.DEBUG, "Namespace 状态上报完成"));
            assertTrue(logs.text().contains("namespaceCode=order_id"));
            assertTrue(logs.text().contains("version=2"));
        }
    }

    @Test void logsActualRemovalAtInfoWithoutRepeatingItForMissingRuntime() {
        loader.loadPublishedNamespaces();
        try (var logs = new LogCapture(NamespaceRuntimeLoader.class, Level.DEBUG)) {
            loader.remove("order_id", "DISABLED");
            loader.remove("order_id", "DISABLED");
            assertTrue(logs.contains(Level.INFO, "Namespace 本地运行时已移除"));
            assertEquals(1, logs.text().lines().filter(line -> line.contains("Namespace 本地运行时已移除")).count());
        }
    }

    @Test
    void shouldKeepSameRuntimeAndSequenceWhenVersionHasNotChanged() {
        loader.loadPublishedNamespaces();
        var runtime = registry.get("order_id").orElseThrow();
        long first = runtime.nextId();
        loader.loadPublishedNamespaces();
        assertSame(runtime, registry.get("order_id").orElseThrow());
        assertEquals(first + 1, runtime.nextId());
        assertEquals(1, configReads.get());
    }

    @Test
    void shouldKeepSequenceWhenPublishingAndRollingBack() {
        loader.refresh("order_id", 2);
        long first = registry.get("order_id").orElseThrow().nextId();
        current.set(namespace(3, 1));
        loader.refresh("order_id", 3);
        assertEquals(first + 1, registry.get("order_id").orElseThrow().nextId());
        current.set(namespace(1, 1));
        loader.refresh("order_id", 3);
        assertEquals(1, registry.get("order_id").orElseThrow().getVersion());
        assertEquals(first + 2, registry.get("order_id").orElseThrow().nextId());
    }

    @Test
    void shouldReadCurrentVersionInsteadOfDelayedMessageVersion() {
        current.set(namespace(3, 1));
        loader.refresh("order_id", 1);
        assertEquals(3, registry.get("order_id").orElseThrow().getVersion());
    }

    @Test
    void shouldIgnoreDelayedDisableAndDeleteWhenDatabaseHasEnabledNamespace() {
        loader.refresh("order_id", 2);
        var runtime = registry.get("order_id").orElseThrow();
        for (String event : List.of("DISABLE", "DELETE")) {
            subscriber.onLocalNamespaceChange(NamespaceChangeMessage.builder()
                    .namespaceCode("order_id").version(1).eventType(event).build());
            assertSame(runtime, registry.get("order_id").orElseThrow());
        }
    }

    @Test
    void shouldRemoveDisabledAndDeletedNamespacesWithoutResettingGenerator() {
        loader.refresh("order_id", 2);
        long first = registry.get("order_id").orElseThrow().nextId();
        current.set(namespace(2, 2));
        loader.loadPublishedNamespaces();
        assertTrue(registry.get("order_id").isEmpty());
        current.set(namespace(2, 1));
        loader.refresh("order_id", 2);
        assertEquals(first + 1, registry.get("order_id").orElseThrow().nextId());
        current.set(null);
        loader.loadPublishedNamespaces();
        assertTrue(registry.get("order_id").isEmpty());
    }

    @Test
    void shouldRecheckDatabaseAfterStaleFullSyncSnapshot() {
        when(namespaces.selectList(any())).thenAnswer(invocation -> {
            NamespaceDO stale = namespace(1, 1);
            current.set(namespace(3, 1));
            return List.of(stale);
        });
        loader.loadPublishedNamespaces();
        assertEquals(3, registry.get("order_id").orElseThrow().getVersion());
    }

    @Test
    void shouldKeepPreviousRuntimeWhenPublishedConfigIsMissing() {
        loader.refresh("order_id", 2);
        var runtime = registry.get("order_id").orElseThrow();
        current.set(namespace(3, 1));
        doReturn(page(null)).when(configs).selectPage(any(), any());
        loader.refresh("order_id", 3);
        assertSame(runtime, registry.get("order_id").orElseThrow());
        verify(statusWriter).saveOrUpdate(argThat(status ->
                "LOAD_FAILED".equals(status.getRuntimeStatus()) && status.getLoadedVersion() == 3));
    }

    @Test
    void shouldKeepPreviousRuntimeWhenConfigCannotBeParsed() {
        loader.refresh("order_id", 2);
        var runtime = registry.get("order_id").orElseThrow();
        current.set(namespace(3, 1));
        doReturn(page(NamespaceConfigDO.builder()
                .namespaceId(1L).version(3).idMode(2).configJson("not-json").build()))
                .when(configs).selectPage(any(), any());
        loader.refresh("order_id", 3);
        assertSame(runtime, registry.get("order_id").orElseThrow());
        verify(statusWriter).saveOrUpdate(argThat(status ->
                "LOAD_FAILED".equals(status.getRuntimeStatus()) && status.getLoadedVersion() == 3));
    }

    @Test
    void shouldSerializeConcurrentRefreshes() throws Exception {
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        when(namespaces.selectPage(any(), any())).thenAnswer(invocation -> {
            if (reads.incrementAndGet() == 1) {
                firstRead.countDown();
                assertTrue(releaseRead.await(5, TimeUnit.SECONDS));
                return page(namespace(1, 1));
            }
            return page(namespace(3, 1));
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> loader.refresh("order_id", 1));
            assertTrue(firstRead.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();
                loader.refresh("order_id", 3);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            long started = System.nanoTime();
            while (secondThread.get().getState() != Thread.State.BLOCKED && reads.get() == 1
                    && System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5)) {
                Thread.onSpinWait();
            }
            assertEquals(Thread.State.BLOCKED, secondThread.get().getState());
            assertEquals(1, reads.get());
            releaseRead.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseRead.countDown();
        }
        assertEquals(3, registry.get("order_id").orElseThrow().getVersion());
    }

    private NamespaceDO namespace(int version, int status) {
        NamespaceDO value = new NamespaceDO();
        value.setId(1L);
        value.setNamespaceCode("order_id");
        value.setCurrentVersion(version);
        value.setStatus(status);
        value.setDeleted(0);
        return value;
    }

    private <T> Page<T> page(T value) {
        Page<T> page = Page.of(1, 1, false);
        page.setRecords(value == null ? List.of() : List.of(value));
        return page;
    }
}
