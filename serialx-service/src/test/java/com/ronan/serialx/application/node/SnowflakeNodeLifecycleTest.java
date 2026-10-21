package com.ronan.serialx.application.node;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.*;
import com.ronan.serialx.config.SnowflakeNodeProperties;
import com.ronan.serialx.config.NodeExecutorConfig;
import com.ronan.serialx.infra.http.OutboundHttpProperties;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import com.ronan.serialx.support.LogCapture;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnowflakeNodeLifecycleTest {
    private final AtomicLong wall = new AtomicLong(SnowflakeGenerator.EPOCH + 1000);
    private final AtomicLong nano = new AtomicLong(1_000_000_000L);
    private final String runtimeId = UUID.randomUUID().toString();
    private final Gateway gateway = new Gateway();
    private final NodeExecutorConfig executors = new NodeExecutorConfig();
    private final ThreadPoolExecutor requests = executors.nodeRequestExecutor();
    private final ScheduledThreadPoolExecutor scheduler = executors.nodeRenewScheduler();

    @AfterEach
    void shutdownExecutors() {
        scheduler.shutdownNow();
        requests.shutdownNow();
    }

    @Test void logsLifecycleTransitionsAndKeepsRenewalAtDebug() {
        try (var logs = new LogCapture(SnowflakeNodeLifecycle.class, Level.DEBUG); var node = node()) {
            node.initialize();
            node.markReady();
            node.renew();
            node.close();
            assertTrue(logs.contains(Level.INFO, "节点注册开始"));
            assertTrue(logs.contains(Level.INFO, "节点注册成功"));
            assertTrue(logs.contains(Level.INFO, "节点发号就绪"));
            assertTrue(logs.contains(Level.DEBUG, "节点续租成功"));
            assertFalse(logs.contains(Level.INFO, "节点续租成功"));
            assertTrue(logs.contains(Level.INFO, "节点关闭开始"));
            assertTrue(logs.contains(Level.INFO, "节点释放成功"));
            assertTrue(logs.text().contains(runtimeId));
            assertTrue(logs.text().contains("datacenterId=1, workerId=2"));
            assertFalse(logs.text().contains("test-token-with-at-least-32-bytes-long"));
        }
    }

    @Test void logsRegistrationFailureWithoutExceptionMessage() {
        gateway.registration = () -> { throw new IllegalStateException("secret-network-payload"); };
        try (var logs = new LogCapture(SnowflakeNodeLifecycle.class, Level.DEBUG); var node = node()) {
            assertThrows(IllegalStateException.class, node::initialize);
            assertTrue(logs.contains(Level.ERROR, "节点注册最终失败"));
            assertFalse(logs.text().contains("secret-network-payload"));
        }
    }

    @Test void lifecycleUsesOneEnumFieldInsteadOfIndependentFlags() {
        var fields = SnowflakeNodeLifecycle.class.getDeclaredFields();
        assertEquals(1, Arrays.stream(fields).filter(field -> field.getType().isEnum()).count());
        assertEquals(0, Arrays.stream(fields).filter(field -> field.getType() == boolean.class).count());
    }

    @Test void refusesBeforeRegistrationAndNamespaceLoading() {
        try (var node = node()) {
            assertFalse(node.isAvailable());
            assertThrows(NodeAllocationException.class, node::generator);
            node.initialize();
            assertFalse(node.isAvailable());
            assertThrows(NodeAllocationException.class, () -> node.generator().nextId());
            node.markReady();
            assertTrue(node.generator().nextId() > 0);
        }
    }

    @Test void expiresAtConservativeDeadlineAndCannotRecover() {
        try (var node = ready()) {
            nano.addAndGet(Duration.ofSeconds(57).toNanos());
            assertFalse(node.isAvailable());
            assertThrows(NodeAllocationException.class, () -> node.generator().nextId());
            node.renew();
            assertFalse(node.isAvailable());
            assertEquals(0, gateway.renewals);
        }
    }

    @Test void responseTimeConsumesLeaseBudget() {
        gateway.registration = () -> { nano.addAndGet(Duration.ofSeconds(6).toNanos()); return lease(60_000, wall.get() + 60_000); };
        try (var node = ready()) {
            nano.addAndGet(Duration.ofSeconds(51).toNanos());
            assertFalse(node.isAvailable());
        }
    }

    @Test void usesActualRemainingLeaseInsteadOfSystemMaximum() {
        gateway.registration = () -> lease(30_000, wall.get() + 60_000);
        try (var node = ready()) {
            nano.addAndGet(Duration.ofSeconds(27).toNanos());
            assertFalse(node.isAvailable());
            assertThrows(NodeAllocationException.class, () -> node.generator().nextId());
        }
    }

    @Test void rejectsLeaseBeyondSystemMaximum() {
        gateway.registration = () -> lease(60_001, wall.get() + 60_001);
        try (var node = node()) {
            assertThrows(IllegalStateException.class, node::initialize);
            assertFalse(node.isAvailable());
            assertEquals(1, gateway.registrations);
        }
    }

    @Test void rejectsLeaseNotLongerThanSystemSafetyMargin() {
        gateway.registration = () -> lease(3_000, wall.get() + 60_000);
        try (var node = node()) {
            assertThrows(IllegalStateException.class, node::initialize);
            assertFalse(node.isAvailable());
            assertEquals(1, gateway.registrations);
        }
    }

    @Test void refusesExactTimestampOutsideConfirmedWindowAndRollback() {
        try (var node = ready()) {
            wall.addAndGet(60_000);
            assertTrue(node.generator().nextId() > 0);
            wall.incrementAndGet();
            assertThrows(NodeAllocationException.class, () -> node.generator().nextId());
            wall.addAndGet(-100);
            assertFalse(node.isAvailable());
        }
        wall.set(SnowflakeGenerator.EPOCH + 1000);
        try (var node = ready()) {
            node.generator().nextId();
            wall.decrementAndGet();
            assertThrows(RuntimeException.class, () -> node.generator().nextId());
            assertFalse(node.isAvailable());
        }
    }

    @Test void transientFailureDoesNotExtendLeaseAnd409IsTerminal() {
        try (var node = ready()) {
            gateway.renewal = () -> { throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503); };
            node.renew();
            assertTrue(node.isAvailable());
            gateway.renewal = () -> { throw new NodeAllocationException(BusinessErrorCode.NODE_LEASE_LOST, 409); };
            node.renew();
            assertFalse(node.isAvailable());
        }
    }

    @Test void delayedRenewalCannotResurrectExpiredSession() throws Exception {
        try (var node = ready(); var threads = Executors.newSingleThreadExecutor()) {
            var requested = new CountDownLatch(1);
            var respond = new CountDownLatch(1);
            gateway.renewal = () -> {
                requested.countDown();
                try { assertTrue(respond.await(2, TimeUnit.SECONDS)); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                return lease(60_000, wall.get() + 120_000);
            };
            var pending = threads.submit(node::renew);
            assertTrue(requested.await(2, TimeUnit.SECONDS));
            nano.addAndGet(Duration.ofSeconds(57).toNanos());
            respond.countDown();
            pending.get(2, TimeUnit.SECONDS);
            assertFalse(node.isAvailable());
        }
    }

    @Test void rejectsIdentityLayoutAndShrinkingRenewalWindow() {
        gateway.registration = () -> {
            var result = lease(60_000, wall.get()+60_000);
            result.setValidFromMs(wall.get());
            result.setWorkerIdBits(6);
            return result;
        };
        try (var node = node()) { assertThrows(RuntimeException.class, node::initialize); }
        gateway.registration = () -> lease(60_000, wall.get()+60_000);
        try (var node = ready()) {
            gateway.renewal = () -> lease(60_000, wall.get()+59_000);
            node.renew();
            assertFalse(node.isAvailable());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrationLeases")
    void invalidRegistrationStopsRetriesWithOriginalError(String scenario, Consumer<NodeLease> corrupt,
                                                         BusinessErrorCode expectedError) {
        gateway.registration = () -> {
            var reply = lease(60_000, wall.get() + 60_000);
            corrupt.accept(reply);
            return reply;
        };
        try (var node = node()) {
            var failure = assertThrows(IllegalStateException.class, node::initialize);
            var cause = assertInstanceOf(NodeAllocationException.class, failure.getCause());
            assertEquals(expectedError.getCode(), cause.getCode());
            assertEquals(503, cause.getHttpStatus());
            assertEquals(1, gateway.registrations);
            assertFalse(node.isAvailable());
        }
    }

    @Test void missingRegistrationLeaseIsTerminal() {
        gateway.registration = () -> null;
        try (var node = node()) {
            var failure = assertThrows(IllegalStateException.class, node::initialize);
            var cause = assertInstanceOf(NodeAllocationException.class, failure.getCause());
            assertEquals(BusinessErrorCode.NODE_LEASE_LOST.getCode(), cause.getCode());
            assertEquals(1, gateway.registrations);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inconsistentRenewalLeases")
    void invalidRenewalCannotBeRevivedByLaterValidResponse(String scenario, Consumer<NodeLease> corrupt,
                                                        BusinessErrorCode expectedError) {
        try (var node = ready()) {
            gateway.renewal = () -> {
                var reply = lease(60_000, wall.get() + 60_000);
                corrupt.accept(reply);
                return reply;
            };
            node.renew();

            assertFalse(node.isAvailable());
            var failure = assertThrows(NodeAllocationException.class, () -> node.generator().nextId());
            assertEquals(expectedError.getCode(), failure.getCode());
            gateway.renewal = () -> lease(60_000, wall.get() + 120_000);
            node.renew();
            assertEquals(1, gateway.renewals);
            assertFalse(node.isAvailable());
        }
    }

    @Test void futureRegistrationTimeCanSucceedAfterClockCatchesUp() {
        long validFrom = wall.get() + 1_000;
        gateway.registration = () -> {
            if (gateway.registrations == 2) {
                wall.set(validFrom);
            }
            var reply = lease(60_000, wall.get() + 60_000);
            reply.setValidFromMs(validFrom);
            return reply;
        };
        try (var node = ready()) {
            assertEquals(2, gateway.registrations);
            assertTrue(node.isAvailable());
            assertTrue(node.generator().nextId() > 0);
        }
    }

    @Test void closeStopsGenerationBeforeReleaseEvenWhenReleaseFails() {
        var node = ready();
        gateway.onRelease = () -> {
            assertFalse(node.isAvailable());
            assertThrows(NodeAllocationException.class, () -> node.generator().nextId());
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        };
        assertDoesNotThrow(node::close);
        assertDoesNotThrow(node::close);
        assertEquals(1, gateway.releases);
    }

    @Test void closeCancelsOnlyItsTaskAndLeavesManagedExecutorsUsable() throws Exception {
        var node = ready();
        assertEquals(1, scheduler.getQueue().size());
        node.close();
        assertTrue(scheduler.getQueue().isEmpty());
        assertFalse(requests.isShutdown());
        assertFalse(scheduler.isShutdown());
        assertEquals("usable", requests.submit(() -> "usable").get(2, TimeUnit.SECONDS));
        assertEquals("usable", scheduler.submit(() -> "usable").get(2, TimeUnit.SECONDS));
        assertEquals(1, gateway.releases);
    }

    @Test void lostNodeCanStillCloseAndReleaseLeaseOnlyOnce() {
        try (var node = ready()) {
            nano.addAndGet(Duration.ofSeconds(57).toNanos());
            assertFalse(node.isAvailable());
            assertThrows(NodeAllocationException.class, node::markReady);
            node.close();
            node.close();
            assertEquals(1, gateway.releases);
            assertTrue(scheduler.getQueue().isEmpty());
        }
        assertEquals(1, gateway.releases);
    }

    @Test void closedNodeRemainsClosedAfterRenewalAttempt() {
        try (var node = ready()) {
            node.close();
            node.renew();
            assertThrows(NodeAllocationException.class, node::markReady);
            assertThrows(NodeAllocationException.class, () -> node.generator().nextId());
            node.close();
            assertEquals(0, gateway.renewals);
            assertEquals(1, gateway.releases);
        }
        assertEquals(1, gateway.releases);
    }

    @ParameterizedTest(name = "迟到续租返回终态错误：{0}")
    @ValueSource(booleans = {false, true})
    void lateRenewalCannotOverwriteClosedState(boolean terminalFailure) throws Exception {
        var taskScheduler = mock(ScheduledExecutorService.class);
        var task = mock(ScheduledFuture.class);
        var requested = new CountDownLatch(1);
        var respond = new CountDownLatch(1);
        when(taskScheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(task);
        when(task.cancel(true)).thenAnswer(invocation -> {
            // 关闭状态已提交后才放行响应，固定续租与关闭的竞争顺序。
            respond.countDown();
            return true;
        });
        gateway.renewal = () -> {
            requested.countDown();
            try {
                assertTrue(respond.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            if (terminalFailure) {
                throw new NodeAllocationException(BusinessErrorCode.NODE_LEASE_LOST, 409);
            }
            return lease(60_000, wall.get() + 120_000);
        };
        try (var node = node(taskScheduler); var threads = Executors.newSingleThreadExecutor()) {
            node.initialize();
            node.markReady();
            var pending = threads.submit(node::renew);
            try {
                assertTrue(requested.await(2, TimeUnit.SECONDS));
                node.close();
                pending.get(2, TimeUnit.SECONDS);
                assertFalse(node.isAvailable());
                assertThrows(NodeAllocationException.class, node::markReady);
                node.close();
                assertEquals(1, gateway.releases);
                verify(task, times(1)).cancel(true);
            } finally {
                respond.countDown();
            }
        }
        assertEquals(1, gateway.releases);
    }

    @Test void registrationSchedulesRenewalWithSystemInterval() {
        var taskScheduler = mock(ScheduledExecutorService.class);
        try (var node = node(taskScheduler)) {
            node.initialize();
            verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(10L), eq(10L), eq(TimeUnit.SECONDS));
        }
    }

    @Test void concurrentCloseCannotMissRenewalTaskBeingRegistered() throws Exception {
        var taskScheduler = mock(ScheduledExecutorService.class);
        var task = mock(ScheduledFuture.class);
        var entered = new CountDownLatch(1);
        var allowRegistration = new CountDownLatch(1);
        var closing = new CountDownLatch(1);
        when(taskScheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertTrue(allowRegistration.await(2, TimeUnit.SECONDS));
                    return task;
                });
        try (var node = node(taskScheduler); var threads = Executors.newFixedThreadPool(2)) {
            var initialized = threads.submit(node::initialize);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var closed = threads.submit(() -> {
                closing.countDown();
                node.close();
            });
            try {
                assertTrue(closing.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> closed.get(100, TimeUnit.MILLISECONDS));
            } finally {
                allowRegistration.countDown();
            }
            initialized.get(2, TimeUnit.SECONDS);
            closed.get(2, TimeUnit.SECONDS);
            verify(task).cancel(true);
            verify(taskScheduler, never()).shutdownNow();
            assertFalse(node.isAvailable());
        } finally {
            allowRegistration.countDown();
        }
    }

    @Test void executorSaturationDoesNotExtendOrCancelCurrentLease() throws Exception {
        try (var node = ready()) {
            var entered = new CountDownLatch(1);
            var unblock = new CountDownLatch(1);
            var drained = new CountDownLatch(1);
            requests.execute(() -> {
                entered.countDown();
                try {
                    unblock.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                requests.execute(drained::countDown);
                node.renew();
                assertTrue(node.isAvailable());
                assertEquals(0, gateway.renewals);
                nano.addAndGet(Duration.ofSeconds(57).toNanos());
                assertFalse(node.isAvailable());
            } finally {
                unblock.countDown();
                assertTrue(drained.await(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test void registrationRetriesAreBoundedAndUseSameRuntimeId() {
        gateway.registration = () -> { throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503); };
        try (var node = node()) {
            assertThrows(RuntimeException.class, node::initialize);
            assertEquals(3, gateway.registrations);
        }
    }

    @Test void interruptedRegistrationStopsRetriesAndPreservesInterruptFlag() {
        Thread startupThread = Thread.currentThread();
        gateway.registration = () -> {
            startupThread.interrupt();
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        };
        try (var node = node()) {
            var failure = assertThrows(IllegalStateException.class, node::initialize);
            assertEquals("节点注册被中断", failure.getCause().getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, gateway.registrations);
            assertFalse(node.isAvailable());
        } finally {
            Thread.interrupted();
        }
    }

    @Test void readinessCannotReviveExpiredLease() {
        try (var node = node()) {
            node.initialize();
            nano.addAndGet(Duration.ofSeconds(57).toNanos());
            assertThrows(NodeAllocationException.class, node::markReady);
            assertFalse(node.isAvailable());
            assertThrows(NodeAllocationException.class, () -> node.generator().nextId());
        }
    }

    @Test void renewalExtendsBudgetAndWindowWithoutResettingSequence() {
        try (var node = ready()) {
            long first = node.generator().nextId();
            nano.addAndGet(Duration.ofSeconds(50).toNanos());
            gateway.renewal = () -> lease(60_000, wall.get()+120_000);
            node.renew();
            assertEquals(first+1, node.generator().nextId());
            nano.addAndGet(Duration.ofSeconds(10).toNanos());
            wall.addAndGet(60_001);
            assertTrue(node.isAvailable());
            assertTrue(node.generator().nextId() > first);
        }
    }

    @Test void futureLowerBoundRetriesWithinAttemptLimitWithoutGeneratingAhead() {
        gateway.registration = () -> {
            var result = lease(60_000, wall.get()+60_000);
            result.setValidFromMs(wall.get()+1000);
            return result;
        };
        try (var node = node()) {
            assertThrows(RuntimeException.class, node::initialize);
            assertEquals(3, gateway.registrations);
            assertFalse(node.isAvailable());
        }
    }

    @Test void registrationElapsedTimeDoesNotReduceConfiguredAttempts() {
        gateway.registration = () -> {
            nano.addAndGet(Duration.ofSeconds(16).toNanos());
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        };
        try (var node = node()) {
            assertThrows(IllegalStateException.class, node::initialize);
            assertEquals(3, gateway.registrations);
            assertFalse(node.isAvailable());
        }
    }

    @Test void registrationElapsedTimeStillConsumesLeaseDuration() {
        gateway.registration = () -> { nano.addAndGet(Duration.ofSeconds(16).toNanos()); return lease(60_000, wall.get()+60_000); };
        try (var node = ready()) {
            assertTrue(node.isAvailable());
            nano.addAndGet(Duration.ofSeconds(41).toNanos());
            assertFalse(node.isAvailable());
        }
    }

    @Test void registrationWaitIsBoundedByUnifiedRequestTimeout() {
        var requested = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        gateway.registration = () -> {
            requested.countDown();
            try {
                unblock.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return lease(60_000, wall.get()+60_000);
        };
        var httpProperties = new OutboundHttpProperties();
        var properties = new SnowflakeNodeProperties(httpProperties);
        properties.setMachineToken("test-token-with-at-least-32-bytes-long");
        httpProperties.setConnectTimeout(Duration.ofMillis(1));
        httpProperties.setRequestTimeout(Duration.ofMillis(100));
        properties.setStartupAttempts(1);
        var identity = mock(ServiceInstanceIdentity.class);
        when(identity.getRuntimeId()).thenReturn(runtimeId);
        when(identity.getInstanceId()).thenReturn("svc-1");
        var clock = mock(NodeClock.class);
        when(clock.wallTimeMillis()).thenAnswer(invocation -> wall.get());
        when(clock.nanoTime()).thenAnswer(invocation -> System.nanoTime());
        try (var node = new SnowflakeNodeLifecycle(gateway, properties, httpProperties, identity,
                clock, requests, scheduler)) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                var failure = assertThrows(IllegalStateException.class, node::initialize);
                assertInstanceOf(NodeAllocationException.class, failure.getCause());
                assertEquals(0, requested.getCount());
                assertEquals(1, gateway.registrations);
                assertFalse(node.isAvailable());
            });
        } finally {
            unblock.countDown();
        }
    }

    private SnowflakeNodeLifecycle ready() {
        var node = node();
        node.initialize();
        node.markReady();
        return node;
    }

    private SnowflakeNodeLifecycle node() {
        return node(scheduler);
    }

    private SnowflakeNodeLifecycle node(ScheduledExecutorService taskScheduler) {
        var httpProperties = new OutboundHttpProperties();
        var properties = new SnowflakeNodeProperties(httpProperties);
        properties.setMachineToken("test-token-with-at-least-32-bytes-long");
        var identity = mock(ServiceInstanceIdentity.class);
        when(identity.getRuntimeId()).thenReturn(runtimeId);
        when(identity.getInstanceId()).thenReturn("service-1");
        var clock = mock(NodeClock.class);
        when(clock.wallTimeMillis()).thenAnswer(invocation -> wall.get());
        when(clock.nanoTime()).thenAnswer(invocation -> nano.get());
        return new SnowflakeNodeLifecycle(gateway, properties, httpProperties, identity,
                clock, requests, taskScheduler);
    }

    private NodeLease lease(long duration, long until) {
        var result = new NodeLease();
        result.setRuntimeId(runtimeId);
        result.setDatacenterId(1);
        result.setWorkerId(2);
        result.setValidFromMs(SnowflakeGenerator.EPOCH);
        result.setReservedUntilMs(until);
        result.setLeaseExpireAtMs(wall.get()+duration);
        result.setLeaseDurationMs(duration);
        result.setEpoch(SnowflakeGenerator.EPOCH);
        result.setWorkerIdBits(5);
        result.setDatacenterIdBits(5);
        result.setSequenceBits(12);
        return result;
    }

    private static Stream<Arguments> invalidRegistrationLeases() {
        return Stream.of(
                leaseViolation("会话不匹配", lease -> lease.setRuntimeId("another-runtime"), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("数据中心编号为负", lease -> lease.setDatacenterId(-1), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("数据中心编号越界", lease -> lease.setDatacenterId(32), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("工作节点编号为负", lease -> lease.setWorkerId(-1), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("工作节点编号越界", lease -> lease.setWorkerId(32), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("纪元不兼容", lease -> lease.setEpoch(SnowflakeGenerator.EPOCH + 1), BusinessErrorCode.NODE_LAYOUT_MISMATCH),
                leaseViolation("工作节点位宽不兼容", lease -> lease.setWorkerIdBits(6), BusinessErrorCode.NODE_LAYOUT_MISMATCH),
                leaseViolation("数据中心位宽不兼容", lease -> lease.setDatacenterIdBits(6), BusinessErrorCode.NODE_LAYOUT_MISMATCH),
                leaseViolation("序列位宽不兼容", lease -> lease.setSequenceBits(13), BusinessErrorCode.NODE_LAYOUT_MISMATCH),
                leaseViolation("窗口起点早于纪元", lease -> lease.setValidFromMs(SnowflakeGenerator.EPOCH - 1), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("窗口倒置", lease -> lease.setValidFromMs(lease.getReservedUntilMs() + 1), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("窗口超出时间容量", lease -> lease.setReservedUntilMs(SnowflakeGenerator.EPOCH + (1L << 41)), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("租约超出授权窗口", lease -> lease.setLeaseExpireAtMs(lease.getReservedUntilMs() + 1), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("租期不足安全余量", lease -> lease.setLeaseDurationMs(3_000), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("租期超出系统上限", lease -> lease.setLeaseDurationMs(60_001), BusinessErrorCode.NODE_LEASE_LOST)
        );
    }

    private static Stream<Arguments> inconsistentRenewalLeases() {
        return Stream.of(
                leaseViolation("续租更换会话", lease -> lease.setRuntimeId("another-runtime"), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("续租更换数据中心", lease -> lease.setDatacenterId(2), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("续租更换工作节点", lease -> lease.setWorkerId(3), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("续租改变窗口起点", lease -> lease.setValidFromMs(lease.getValidFromMs() + 1), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("续租缩小预留窗口", lease -> {
                    lease.setReservedUntilMs(lease.getReservedUntilMs() - 1);
                    lease.setLeaseExpireAtMs(lease.getLeaseExpireAtMs() - 1);
                }, BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("续租回退到期时间", lease -> lease.setLeaseExpireAtMs(lease.getLeaseExpireAtMs() - 1), BusinessErrorCode.NODE_LEASE_LOST),
                leaseViolation("续租返回不兼容布局", lease -> lease.setSequenceBits(13), BusinessErrorCode.NODE_LEASE_LOST)
        );
    }

    private static Arguments leaseViolation(String scenario, Consumer<NodeLease> corrupt, BusinessErrorCode error) {
        return Arguments.of(scenario, corrupt, error);
    }

    private class Gateway implements NodeAllocationGateway {
        Supplier<NodeLease> registration = () -> lease(60_000, wall.get()+60_000);
        Supplier<NodeLease> renewal = registration;
        Runnable onRelease = () -> {};
        int registrations, renewals, releases;
        public NodeLease register(NodeRegisterRequest request) { registrations++; assertEquals(runtimeId, request.getRuntimeId()); return registration.get(); }
        public NodeLease renew(NodeIdentityRequest request) { renewals++; return renewal.get(); }
        public void release(NodeIdentityRequest request) { releases++; onRelease.run(); }
    }
}
