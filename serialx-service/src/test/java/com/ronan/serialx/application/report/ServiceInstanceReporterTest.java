package com.ronan.serialx.application.report;

import com.ronan.serialx.adapter.redis.ServiceInstanceStatusWriterImpl;
import com.ronan.serialx.application.node.SnowflakeNodeLifecycle;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.domain.model.ServiceInstanceInfo;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.domain.runtime.SegmentNamespaceRuntime;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import com.ronan.serialx.support.LogCapture;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceInstanceReporterTest {
    @Test void heartbeatDetailsCanBeEnabledAndSuppressedByLoggerLevel() throws Exception {
        var identity = mock(ServiceInstanceIdentity.class);
        when(identity.getInstanceId()).thenReturn("svc-1");
        when(identity.getRuntimeId()).thenReturn("runtime-1");
        var writer = mock(ServiceInstanceStatusWriterImpl.class);
        when(writer.tryAcquireHeartbeat(any(), any())).thenReturn(true);
        var node = mock(SnowflakeNodeLifecycle.class);
        when(node.isAvailable()).thenReturn(true);
        var registry = new NamespaceRegistry();
        registry.replace("order_id", new SegmentNamespaceRuntime("order_id", 1, 1, 0, 100));
        var reporter = new ServiceInstanceReporter(new ServiceInstanceProperties(), identity, writer, registry, node);
        try (var logs = new LogCapture(ServiceInstanceReporter.class, Level.INFO)) {
            reporter.registeredInstance();
            reporter.scheduledReportStatus();
            assertTrue(logs.contains(Level.INFO, "实例上报已启用"));
            assertFalse(logs.contains(Level.DEBUG, "实例状态上报完成"));
        }
        try (var logs = new LogCapture(ServiceInstanceReporter.class, Level.DEBUG)) {
            reporter.scheduledReportStatus();
            assertTrue(logs.contains(Level.DEBUG, "实例状态上报完成"));
            assertTrue(logs.contains(Level.DEBUG, "Namespace 状态上报完成"));
            assertTrue(logs.text().contains("runtimeId=runtime-1"));
            assertTrue(logs.text().contains("namespaceCode=order_id"));
            assertFalse(logs.contains(Level.INFO, "实例状态上报完成"));
        }
        reporter.stop();
    }

    @Test void heartbeatUsesFixedSystemIntervalInsteadOfPropertyExpression() throws Exception {
        var scheduled = ServiceInstanceReporter.class.getMethod("scheduledReportStatus").getAnnotation(Scheduled.class);
        assertEquals(20L, scheduled.fixedDelay());
        assertEquals(TimeUnit.SECONDS, scheduled.timeUnit());
        assertEquals("", scheduled.fixedDelayString());
    }

    @Test void sharesRuntimeIdAndReportsUnreadyInsteadOfActive() throws Exception {
        var identity = mock(ServiceInstanceIdentity.class);
        when(identity.getInstanceId()).thenReturn("svc-1");
        when(identity.getRuntimeId()).thenReturn("runtime-1");
        when(identity.getIp()).thenReturn("127.0.0.1");
        var writer = mock(ServiceInstanceStatusWriterImpl.class);
        when(writer.tryAcquireHeartbeat("svc-1", "runtime-1")).thenReturn(true);
        var registry = new NamespaceRegistry();
        registry.replace("segment", new SegmentNamespaceRuntime("segment", 1, 1, 0, 100));
        var node = mock(SnowflakeNodeLifecycle.class);
        var reporter = new ServiceInstanceReporter(new ServiceInstanceProperties(), identity, writer, registry, node);
        reporter.registeredInstance();
        var snapshot = ArgumentCaptor.forClass(ServiceInstanceInfo.class);
        verify(writer).saveOnline(snapshot.capture());
        assertEquals("runtime-1", snapshot.getValue().getRuntimeId());
        assertEquals("NODE_NOT_READY", snapshot.getValue().getStatus());
        var status = ArgumentCaptor.forClass(com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO.class);
        verify(writer).saveOrUpdate(status.capture());
        assertEquals("UNSUPPORTED", status.getValue().getRuntimeStatus());
        reporter.stop();
        var order = inOrder(node, writer);
        order.verify(node).close();
        order.verify(writer).saveOffline(any());
    }

    @Test void redisOutageDoesNotAffectMysqlLeaseOrPreventShutdown() {
        var identity = mock(ServiceInstanceIdentity.class);
        when(identity.getInstanceId()).thenReturn("svc-1");
        when(identity.getRuntimeId()).thenReturn("runtime-1");
        var writer = mock(ServiceInstanceStatusWriterImpl.class);
        when(writer.tryAcquireHeartbeat(any(), any())).thenThrow(new IllegalStateException("Redis unavailable"));
        var node = mock(SnowflakeNodeLifecycle.class);
        var reporter = new ServiceInstanceReporter(new ServiceInstanceProperties(), identity, writer, new NamespaceRegistry(), node);
        assertDoesNotThrow(reporter::registeredInstance);
        assertDoesNotThrow(reporter::scheduledReportStatus);
        assertDoesNotThrow(() -> { reporter.stop(); });
        verify(node).close();
    }
}
