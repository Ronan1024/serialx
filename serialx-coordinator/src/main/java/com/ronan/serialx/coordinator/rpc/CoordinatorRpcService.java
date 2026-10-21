package com.ronan.serialx.coordinator.rpc;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.NodeAllocationException;
import com.ronan.serialx.common.node.NodeIdentityRequest;
import com.ronan.serialx.common.node.NodeLease;
import com.ronan.serialx.common.node.NodeRegisterRequest;
import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.ronan.serialx.protocol.coordination.v1.*;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.util.function.Supplier;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/** 协议适配层：所有租约响应均来自协调用例已提交结果，不在 RPC 进程内保存所有权。 */
@Component
public class CoordinatorRpcService extends CoordinatorGrpc.CoordinatorImplBase {
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final SnowflakeNodeManager manager;
    private final CoordinatorGrpcProperties properties;
    /** 跟踪真实处理而非网络存活，防止 deadline 提前释放 JDBC 的并发预算。 */
    private final Semaphore inFlight;

    public CoordinatorRpcService(SnowflakeNodeManager manager, CoordinatorGrpcProperties properties) {
        this.manager = manager;
        this.properties = properties;
        this.inFlight = new Semaphore(properties.getMaxInFlight());
    }

    @Override
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> observer) {
        execute(observer, () -> {
            metadata(request.getMetadata());
            session(request.getSession());
            if (request.getResourcesCount() != 1 || request.getResources(0) != ResourceRequirement.SNOWFLAKE
                    || request.getEndpointsCount() != 0) {
                throw Status.UNIMPLEMENTED.withDescription("R1 只支持雪花租约，不注册服务端点或 Segment 会话").asRuntimeException();
            }
            NodeLease lease = manager.register(new NodeRegisterRequest(request.getSession().getRuntimeId(),
                    request.getSession().getInstanceId()));
            return RegisterResponse.newBuilder().setSession(request.getSession()).setSnowflakeLease(lease(lease))
                    .addCapabilities(CoordinationCapability.LEGACY_SNOWFLAKE_LEASE).build();
        });
    }

    @Override
    public void renew(RenewRequest request, StreamObserver<RenewResponse> observer) {
        execute(observer, () -> {
            metadata(request.getMetadata());
            session(request.getSession());
            NodeLease lease = manager.renew(identity(request.getSession(), request.getSnowflake()), actor().instanceId());
            return RenewResponse.newBuilder().setSession(request.getSession()).setSnowflakeLease(lease(lease)).build();
        });
    }

    @Override
    public void release(ReleaseRequest request, StreamObserver<ReleaseResponse> observer) {
        execute(observer, () -> {
            metadata(request.getMetadata());
            session(request.getSession());
            manager.release(identity(request.getSession(), request.getSnowflake()), actor().instanceId());
            return ReleaseResponse.newBuilder().setReleased(true).build();
        });
    }

    @Override
    public void report(ReportRequest request, StreamObserver<ReportResponse> observer) {
        execute(observer, () -> { metadata(request.getMetadata()); session(request.getSession()); return unavailable("Report 属于 R4"); });
    }

    @Override
    public void allocateSegment(AllocateSegmentRequest request, StreamObserver<AllocateSegmentResponse> observer) {
        execute(observer, () -> { metadata(request.getMetadata()); session(request.getSession()); return unavailable("AllocateSegment 属于 R7"); });
    }

    @Override
    public void getNamespaceConfig(GetNamespaceConfigRequest request, StreamObserver<GetNamespaceConfigResponse> observer) {
        execute(observer, () -> { metadata(request.getMetadata()); session(request.getSession()); return unavailable("GetNamespaceConfig 尚未实现"); });
    }

    @Override
    public void getRoutes(GetRoutesRequest request, StreamObserver<RoutesResponse> observer) {
        execute(observer, () -> { metadata(request.getMetadata()); namespace(request.getNamespaceCode()); return unavailable("路由发布属于 R5"); });
    }

    @Override
    public void watchRoutes(WatchRoutesRequest request, StreamObserver<RoutesResponse> observer) {
        execute(observer, () -> {
            metadata(request.getMetadata());
            if (request.getNamespaceCodesCount() == 0) throw Status.INVALID_ARGUMENT.withDescription("发现范围不能为空").asRuntimeException();
            request.getNamespaceCodesList().forEach(this::namespace);
            return unavailable("路由订阅属于 R5");
        });
    }

    private void metadata(RequestMetadata metadata) {
        if (metadata.getProtocolVersion() != 1) throw Status.INVALID_ARGUMENT.withDescription("不支持的协议版本").asRuntimeException();
        if (!UUID.matcher(metadata.getRequestId()).matches()) throw Status.INVALID_ARGUMENT.withDescription("requestId 必须为小写标准 UUID").asRuntimeException();
        if (!properties.getAllocationDomain().equals(metadata.getAllocationDomain())) {
            throw Status.PERMISSION_DENIED.withDescription("机器身份未授权此分配域").asRuntimeException();
        }
    }

    private void session(SessionIdentity session) {
        if (!actor().instanceId().equals(session.getInstanceId())) {
            throw Status.PERMISSION_DENIED.withDescription("机器身份与实例标识不匹配").asRuntimeException();
        }
        if (!UUID.matcher(session.getRuntimeId()).matches()) throw Status.INVALID_ARGUMENT.withDescription("runtimeId 必须为小写标准 UUID").asRuntimeException();
        if (session.hasGeneration()) throw Status.UNIMPLEMENTED.withDescription("R1 未实现 generation fencing，请勿发送伪造代数").asRuntimeException();
    }

    private void namespace(String namespace) {
        if (namespace.isBlank()) throw Status.INVALID_ARGUMENT.withDescription("namespaceCode 不能为空").asRuntimeException();
        if (!actor().allowedNamespaces().contains(namespace)) throw Status.PERMISSION_DENIED.withDescription("发现身份无此 namespace 权限").asRuntimeException();
    }

    private static CoordinatorRpcInterceptor.Actor actor() {
        CoordinatorRpcInterceptor.Actor actor = CoordinatorRpcInterceptor.ACTOR.get();
        if (actor == null) throw Status.UNAUTHENTICATED.withDescription("机器身份缺失").asRuntimeException();
        return actor;
    }

    private static NodeIdentityRequest identity(SessionIdentity session, SnowflakeResourceIdentity resource) {
        return new NodeIdentityRequest(session.getRuntimeId(), resource.hasDatacenterId() ? resource.getDatacenterId() : null,
                resource.hasWorkerId() ? resource.getWorkerId() : null);
    }

    private static SnowflakeLease lease(NodeLease lease) {
        return SnowflakeLease.newBuilder().setNodeId((lease.getDatacenterId() << 5) | lease.getWorkerId())
                .setDatacenterId(lease.getDatacenterId()).setWorkerId(lease.getWorkerId()).setValidFromMs(lease.getValidFromMs())
                .setReservedUntilMs(lease.getReservedUntilMs()).setLeaseExpireAtMs(lease.getLeaseExpireAtMs())
                .setRemainingLeaseMs(lease.getLeaseDurationMs()).setEpoch(lease.getEpoch()).setWorkerIdBits(lease.getWorkerIdBits())
                .setDatacenterIdBits(lease.getDatacenterIdBits()).setSequenceBits(lease.getSequenceBits()).build();
    }

    private static <T> T unavailable(String message) { throw Status.UNIMPLEMENTED.withDescription(message).asRuntimeException(); }

    /** 数据库异常不暴露 SQL/连接配置；deadline 失败不代表事务一定未提交，调用方须保守处理。 */
    private <T> void execute(StreamObserver<T> observer, Supplier<T> action) {
        if (!inFlight.tryAcquire()) {
            observer.onError(Status.RESOURCE_EXHAUSTED.withDescription("协调处理并发预算耗尽").asRuntimeException());
            return;
        }
        try {
            if (Context.current().isCancelled()) throw Status.DEADLINE_EXCEEDED.asRuntimeException();
            T response = action.get();
            if (!Context.current().isCancelled()) { observer.onNext(response); observer.onCompleted(); }
        } catch (StatusRuntimeException ex) {
            observer.onError(ex);
        } catch (NodeAllocationException ex) {
            Status status = switch (ex.getHttpStatus()) {
                case 400 -> Status.INVALID_ARGUMENT;
                case 401 -> Status.UNAUTHENTICATED;
                case 403 -> Status.PERMISSION_DENIED;
                case 409 -> Status.FAILED_PRECONDITION;
                case 503 -> BusinessErrorCode.NODE_POOL_EXHAUSTED.getCode().equals(ex.getCode())
                        ? Status.RESOURCE_EXHAUSTED : Status.UNAVAILABLE;
                default -> Status.INTERNAL;
            };
            observer.onError(status.withDescription("节点协调请求被拒绝").asRuntimeException());
        } catch (DataAccessException | TransactionException ex) {
            observer.onError(Status.UNAVAILABLE.withDescription("协调主库暂不可用").asRuntimeException());
        } catch (RuntimeException ex) {
            observer.onError(Status.INTERNAL.withDescription("协调请求处理失败").asRuntimeException());
        } finally {
            // JDBC 不保证响应取消时立即退出；配额只能在真实业务调用结束后归还。
            inFlight.release();
        }
    }
}
