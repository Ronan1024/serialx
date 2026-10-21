package com.ronan.serialx.coordinator.rpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.CertificateParsingException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** mTLS 身份、角色和服务端 deadline 共用的信任边界；不读取后台 JWT。 */
public final class CoordinatorRpcInterceptor implements ServerInterceptor {
    static final Context.Key<Actor> ACTOR = Context.key("serialx-coordinator-machine");
    private static final Set<String> NODE_METHODS = Set.of("Register", "Renew", "Report", "Release",
            "AllocateSegment", "GetNamespaceConfig");
    private static final Set<String> DISCOVERY_METHODS = Set.of("GetRoutes", "WatchRoutes");
    private final CoordinatorGrpcProperties properties;
    private final ScheduledExecutorService deadlines;

    /** 认证信息只包含授权结果，不保存证书私钥、令牌或未经信任的请求头。 */
    record Actor(String instanceId, Set<CoordinatorGrpcProperties.Role> roles, Set<String> allowedNamespaces) { }

    public CoordinatorRpcInterceptor(CoordinatorGrpcProperties properties, ScheduledExecutorService deadlines) {
        this.properties = properties;
        this.deadlines = deadlines;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        Actor actor;
        try {
            actor = authenticate(call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION));
        } catch (SSLPeerUnverifiedException | CertificateParsingException | IllegalArgumentException ex) {
            call.close(Status.UNAUTHENTICATED.withDescription("机器证书身份未授权"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        String fullMethod = call.getMethodDescriptor().getFullMethodName();
        String method = fullMethod.substring(fullMethod.lastIndexOf('/') + 1);
        CoordinatorGrpcProperties.Role required = NODE_METHODS.contains(method) ? CoordinatorGrpcProperties.Role.NODE
                : DISCOVERY_METHODS.contains(method) ? CoordinatorGrpcProperties.Role.DISCOVERY : null;
        if (required == null || !actor.roles().contains(required)) {
            call.close(Status.PERMISSION_DENIED.withDescription("机器身份无此操作权限"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        // 自动取客户端 deadline 与处理预算的较小值；此前排队由客户端总 deadline 限制。
        Context.CancellableContext context = Context.current().withValue(ACTOR, actor).withDeadlineAfter(
                properties.getMaxRequestDuration().toNanos(), TimeUnit.NANOSECONDS, deadlines);
        AtomicBoolean finished = new AtomicBoolean();
        ServerCall<ReqT, RespT> guarded = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                if (finished.compareAndSet(false, true)) {
                    try { super.close(status, trailers); }
                    finally { context.cancel(null); }
                }
            }
        };
        context.addListener(cancelled -> guarded.close(
                cancelled.getDeadline() != null && cancelled.getDeadline().isExpired()
                        ? Status.DEADLINE_EXCEEDED.withDescription("协调 RPC 请求截止") : Status.CANCELLED,
                new Metadata()), Runnable::run);
        try {
            ServerCall.Listener<ReqT> listener = Contexts.interceptCall(context, guarded, headers, next);
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
                @Override
                public void onCancel() {
                    try { super.onCancel(); }
                    finally { context.cancel(null); }
                    // 连接断开只结束请求，不调用 Release；租约由主库期限及数据面预算决定。
                }
                @Override
                public void onComplete() {
                    try { super.onComplete(); }
                    finally { context.cancel(null); }
                }
            };
        } catch (RuntimeException ex) {
            guarded.close(Status.INTERNAL.withDescription("协调 RPC 请求处理失败"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
    }

    private Actor authenticate(SSLSession ssl) throws SSLPeerUnverifiedException, CertificateParsingException {
        if (ssl == null) throw new IllegalArgumentException("缺失 TLS 会话");
        Certificate[] peers = ssl.getPeerCertificates();
        if (peers == null || peers.length == 0 || !(peers[0] instanceof X509Certificate certificate)) {
            throw new IllegalArgumentException("缺失机器证书");
        }
        Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
        if (sans == null) throw new IllegalArgumentException("缺失 URI SAN");
        CoordinatorGrpcProperties.MachineIdentity matched = null;
        for (List<?> san : sans) {
            if (san.size() < 2 || !Integer.valueOf(6).equals(san.get(0)) || !(san.get(1) instanceof String uri)) continue;
            for (CoordinatorGrpcProperties.MachineIdentity identity : properties.getIdentities().values()) {
                if (!uri.equals(identity.getCertificateUri())) continue;
                // 多个不同授权 URI 同时存在时拒绝歧义，不能把多个身份的权限叠加。
                if (matched != null && matched != identity) throw new IllegalArgumentException("证书身份有歧义");
                matched = identity;
            }
        }
        if (matched == null) throw new IllegalArgumentException("机器身份不在授权列表");
        return new Actor(matched.getInstanceId(), Set.copyOf(matched.getRoles()), Set.copyOf(matched.getAllowedNamespaces()));
    }
}
