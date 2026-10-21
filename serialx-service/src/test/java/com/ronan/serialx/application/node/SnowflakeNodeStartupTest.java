package com.ronan.serialx.application.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.adapter.redis.ServiceInstanceStatusWriterImpl;
import com.ronan.serialx.application.refresh.NamespaceRuntimeLoader;
import com.ronan.serialx.common.node.*;
import com.ronan.serialx.config.*;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.domain.factory.NamespaceRuntimeFactory;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.infra.mapper.NamespaceMapper;
import com.ronan.serialx.infra.mapper.NamespaceConfigMapper;
import com.ronan.serialx.infra.http.OutboundHttpProperties;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SnowflakeNodeStartupTest {
    @Test void registersBeforeGeneratorAndLoadsBeforeReadinessWithoutCircularBeans() {
        var requests = new AtomicReference<ThreadPoolExecutor>();
        var scheduler = new AtomicReference<ScheduledThreadPoolExecutor>();
        var gatewayRef = new AtomicReference<NodeAllocationGateway>();
        new ApplicationContextRunner()
                .withUserConfiguration(Dependencies.class, SnowflakeNodeLifecycle.class,
                        SnowflakeNodeStartupRunner.class, NodeClock.class,
                        SnowflakeGeneratorConfig.class, NamespaceRuntimeFactory.class, NodeExecutorConfig.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    requests.set(context.getBean("nodeRequestExecutor", ThreadPoolExecutor.class));
                    scheduler.set(context.getBean("nodeRenewScheduler", ScheduledThreadPoolExecutor.class));
                    var lifecycle = context.getBean(SnowflakeNodeLifecycle.class);
                    var generator = context.getBean(SnowflakeGenerator.class);
                    assertSame(generator, lifecycle.generator());
                    assertFalse(lifecycle.isAvailable());
                    assertThrows(NodeAllocationException.class, generator::nextId);
                    var loader = context.getBean(NamespaceRuntimeLoader.class);
                    doAnswer(invocation -> {
                        assertFalse(lifecycle.isAvailable(), "配置加载完成前不能开放发号");
                        assertThrows(NodeAllocationException.class, generator::nextId);
                        return invocation.callRealMethod();
                    }).when(loader).loadPublishedNamespaces();
                    context.getBean(SnowflakeNodeStartupRunner.class).run(null);
                    assertTrue(lifecycle.isAvailable());
                    assertTrue(generator.nextId() > 0);
                    verify(context.getBean(NamespaceRuntimeLoader.class)).loadPublishedNamespaces();
                    var gateway = context.getBean(NodeAllocationGateway.class);
                    gatewayRef.set(gateway);
                    doAnswer(invocation -> {
                        assertFalse(requests.get().isShutdown(), "必须先释放租约，再关闭请求执行器");
                        return null;
                    }).when(gateway).release(any());
                });
        assertTrue(requests.get().isShutdown());
        assertTrue(scheduler.get().isShutdown());
        verify(gatewayRef.get()).release(any());
    }

    @Test void namespaceLoadingFailureClosesNodeAndPreservesOriginalFailure() {
        new ApplicationContextRunner()
                .withUserConfiguration(Dependencies.class, SnowflakeNodeLifecycle.class,
                        SnowflakeNodeStartupRunner.class, NodeClock.class,
                        SnowflakeGeneratorConfig.class, NamespaceRuntimeFactory.class, NodeExecutorConfig.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var lifecycle = context.getBean(SnowflakeNodeLifecycle.class);
                    var failure = new IllegalStateException("MySQL unavailable");
                    doThrow(failure).when(context.getBean(NamespaceRuntimeLoader.class)).loadPublishedNamespaces();

                    assertSame(failure, assertThrows(IllegalStateException.class,
                            () -> context.getBean(SnowflakeNodeStartupRunner.class).run(null)));

                    assertFalse(lifecycle.isAvailable());
                    assertThrows(NodeAllocationException.class, () -> lifecycle.generator().nextId());
                    assertTrue(context.getBean("nodeRenewScheduler", ScheduledThreadPoolExecutor.class).getQueue().isEmpty());
                    verify(context.getBean(NodeAllocationGateway.class)).release(any());
                });
    }

    @Test void closedNodeCannotBeReopenedAfterNamespaceLoading() {
        new ApplicationContextRunner()
                .withUserConfiguration(Dependencies.class, SnowflakeNodeLifecycle.class,
                        SnowflakeNodeStartupRunner.class, NodeClock.class,
                        SnowflakeGeneratorConfig.class, NamespaceRuntimeFactory.class, NodeExecutorConfig.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var lifecycle = context.getBean(SnowflakeNodeLifecycle.class);
                    doAnswer(invocation -> {
                        lifecycle.close();
                        return null;
                    }).when(context.getBean(NamespaceRuntimeLoader.class)).loadPublishedNamespaces();

                    assertThrows(NodeAllocationException.class,
                            () -> context.getBean(SnowflakeNodeStartupRunner.class).run(null));

                    assertFalse(lifecycle.isAvailable());
                    verify(context.getBean(NodeAllocationGateway.class)).release(any());
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean OutboundHttpProperties httpProperties() { return new OutboundHttpProperties(); }
        @Bean SnowflakeNodeProperties properties(OutboundHttpProperties httpProperties) {
            var properties = new SnowflakeNodeProperties(httpProperties);
            properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
            return properties;
        }
        @Bean ServiceInstanceIdentity identity() {
            var properties = new ServiceInstanceProperties();
            properties.setInstanceId("test-service");
            properties.setAdvertisedIp("127.0.0.1");
            return new ServiceInstanceIdentity(properties, new MockEnvironment());
        }
        @Bean NodeAllocationGateway gateway() {
            var gateway = mock(NodeAllocationGateway.class);
            when(gateway.register(any())).thenAnswer(invocation -> {
                NodeRegisterRequest request = invocation.getArgument(0);
                long now = System.currentTimeMillis();
                var result = new NodeLease();
                result.setRuntimeId(request.getRuntimeId());
                result.setDatacenterId(0);
                result.setWorkerId(7);
                result.setValidFromMs(SnowflakeGenerator.EPOCH);
                result.setReservedUntilMs(now+60_000);
                result.setLeaseExpireAtMs(now+60_000);
                result.setLeaseDurationMs(60_000);
                result.setEpoch(SnowflakeGenerator.EPOCH);
                result.setWorkerIdBits(5);
                result.setDatacenterIdBits(5);
                result.setSequenceBits(12);
                return result;
            });
            return gateway;
        }
        @Bean NamespaceRuntimeLoader loader(NamespaceRuntimeFactory factory, ServiceInstanceIdentity identity) {
            var namespaces = mock(NamespaceMapper.class);
            when(namespaces.selectList(any())).thenReturn(List.of());
            return spy(new NamespaceRuntimeLoader(namespaces, mock(NamespaceConfigMapper.class), factory,
                    new NamespaceRegistry(), mock(ServiceInstanceStatusWriterImpl.class),
                    new ServiceInstanceProperties(), identity, new ObjectMapper()));
        }
    }
}
