package com.ronan.serialx.coordinator.rpc;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.NodeAllocationException;
import com.ronan.serialx.common.node.NodeLease;
import com.ronan.serialx.common.node.NodeRegisterRequest;
import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.ronan.serialx.protocol.coordination.v1.*;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅验证协议适配与信任边界；真实 TLS/MySQL/跨 admin 验证由 R1 手工计划覆盖。 */
class CoordinatorRpcServiceTest {
    private static final String RUNTIME = "11111111-1111-1111-1111-111111111111";
    private final SnowflakeNodeManager manager = mock(SnowflakeNodeManager.class);
    private final CoordinatorRpcService service = new CoordinatorRpcService(manager, new CoordinatorGrpcProperties());

    @Test
    void shouldReturnCommittedLeaseWithoutInventingGeneration() {
        when(manager.register(new NodeRegisterRequest(RUNTIME, "node-a")))
                .thenReturn(lease());
        var observer = new Result<RegisterResponse>();
        node().run(() -> service.register(register(session()), observer));
        assertNull(observer.failure);
        assertTrue(observer.complete);
        assertEquals(34, observer.response.getSnowflakeLease().getNodeId());
        assertEquals(60, observer.response.getSnowflakeLease().getRemainingLeaseMs());
        assertFalse(observer.response.getSession().hasGeneration());
        assertEquals(CoordinationCapability.LEGACY_SNOWFLAKE_LEASE, observer.response.getCapabilities(0));
    }

    @Test
    void shouldRejectBodyIdentitySpoofAndGenerationBeforeCallingManager() {
        var spoof = new Result<RegisterResponse>();
        node().run(() -> service.register(register(session().toBuilder().setInstanceId("node-b").build()), spoof));
        assertEquals(Status.Code.PERMISSION_DENIED, Status.fromThrowable(spoof.failure).getCode());
        var generation = new Result<RegisterResponse>();
        node().run(() -> service.register(register(session().toBuilder().setGeneration(0).build()), generation));
        assertEquals(Status.Code.UNIMPLEMENTED, Status.fromThrowable(generation.failure).getCode());
        verifyNoInteractions(manager);
    }

    @Test
    void shouldMapPoolCapacityDifferentlyFromStorageOutage() {
        when(manager.register(any())).thenThrow(new NodeAllocationException(BusinessErrorCode.NODE_POOL_EXHAUSTED, 503));
        var observer = new Result<RegisterResponse>();
        node().run(() -> service.register(register(session()), observer));
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, Status.fromThrowable(observer.failure).getCode());
    }

    @Test
    void shouldAuthorizeDiscoveryScopeWithoutReturningFakeEmptyRoutes() {
        Context discovery = Context.current().withValue(CoordinatorRpcInterceptor.ACTOR,
                new CoordinatorRpcInterceptor.Actor(null, Set.of(CoordinatorGrpcProperties.Role.DISCOVERY), Set.of("orders")));
        var forbidden = new Result<RoutesResponse>();
        discovery.run(() -> service.getRoutes(GetRoutesRequest.newBuilder().setMetadata(metadata()).setNamespaceCode("users").build(), forbidden));
        assertEquals(Status.Code.PERMISSION_DENIED, Status.fromThrowable(forbidden.failure).getCode());
        var pending = new Result<RoutesResponse>();
        discovery.run(() -> service.getRoutes(GetRoutesRequest.newBuilder().setMetadata(metadata()).setNamespaceCode("orders").build(), pending));
        assertEquals(Status.Code.UNIMPLEMENTED, Status.fromThrowable(pending.failure).getCode());
        assertNull(pending.response);
    }

    @Test
    void shouldRejectUnsupportedVersion() {
        var observer = new Result<RegisterResponse>();
        node().run(() -> service.register(register(session()).toBuilder().setMetadata(metadata().toBuilder().setProtocolVersion(2)).build(), observer));
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(observer.failure).getCode());
        verifyNoInteractions(manager);
    }

    @Test
    void shouldKeepBusinessPermitUntilTimedOutDatabaseWorkReallyExits() throws Exception {
        var properties = new CoordinatorGrpcProperties();
        properties.setMaxInFlight(1);
        var bounded = new CoordinatorRpcService(manager, properties);
        var entered = new CountDownLatch(1);
        var exit = new CountDownLatch(1);
        var deadlineReached = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        when(manager.register(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(exit.await(3, TimeUnit.SECONDS));
            return lease();
        });
        Context.CancellableContext context = node().withDeadlineAfter(500, TimeUnit.MILLISECONDS, scheduler);
        context.addListener(cancelled -> deadlineReached.countDown(), Runnable::run);
        try {
            var first = executor.submit(() -> context.run(() -> bounded.register(register(session()), new Result<>())));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(deadlineReached.await(2, TimeUnit.SECONDS));
            var second = new Result<RegisterResponse>();
            node().run(() -> bounded.register(register(session()), second));
            assertEquals(Status.Code.RESOURCE_EXHAUSTED, Status.fromThrowable(second.failure).getCode());
            exit.countDown();
            first.get(2, TimeUnit.SECONDS);
            var third = new Result<RegisterResponse>();
            node().run(() -> bounded.register(register(session()), third));
            assertNull(third.failure);
            assertTrue(third.complete);
        } finally {
            exit.countDown();
            context.cancel(null);
            executor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    private static NodeLease lease() {
        var lease = new NodeLease();
        lease.setRuntimeId(RUNTIME);
        lease.setDatacenterId(1);
        lease.setWorkerId(2);
        lease.setValidFromMs(100);
        lease.setReservedUntilMs(200);
        lease.setLeaseExpireAtMs(190);
        lease.setLeaseDurationMs(60);
        lease.setEpoch(0);
        lease.setWorkerIdBits(5);
        lease.setDatacenterIdBits(5);
        lease.setSequenceBits(12);
        return lease;
    }

    private static Context node() {
        return Context.current().withValue(CoordinatorRpcInterceptor.ACTOR,
                new CoordinatorRpcInterceptor.Actor("node-a", Set.of(CoordinatorGrpcProperties.Role.NODE), Set.of()));
    }
    private static SessionIdentity session() { return SessionIdentity.newBuilder().setInstanceId("node-a").setRuntimeId(RUNTIME).build(); }
    private static RequestMetadata metadata() { return RequestMetadata.newBuilder().setProtocolVersion(1).setRequestId(RUNTIME).setAllocationDomain("default").build(); }
    private static RegisterRequest register(SessionIdentity session) { return RegisterRequest.newBuilder().setMetadata(metadata()).setSession(session).addResources(ResourceRequirement.SNOWFLAKE).build(); }

    private static final class Result<T> implements StreamObserver<T> {
        T response;
        Throwable failure;
        boolean complete;
        @Override public void onNext(T value) { response = value; }
        @Override public void onError(Throwable error) { failure = error; }
        @Override public void onCompleted() { complete = true; }
    }
}
