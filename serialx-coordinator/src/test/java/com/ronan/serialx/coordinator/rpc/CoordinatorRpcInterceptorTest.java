package com.ronan.serialx.coordinator.rpc;

import com.ronan.serialx.protocol.coordination.v1.RegisterRequest;
import com.ronan.serialx.protocol.coordination.v1.RegisterResponse;
import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 连接认证逻辑的单元边界，不能替代测试计划中的真实 TLS 握手校验。 */
class CoordinatorRpcInterceptorTest {
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    @AfterEach void close() { deadlines.shutdownNow(); }

    @Test
    void shouldRejectMissingTlsInsteadOfAcceptingJwtMetadata() {
        var call = call(null);
        var headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer not-a-machine-certificate");
        interceptor(CoordinatorGrpcProperties.Role.NODE).interceptCall(call, headers, handler());
        assertClosed(call, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void shouldRejectDiscoveryCertificateOnNodeOperation() throws Exception {
        var call = call(certificate("spiffe://serialx/node-a"));
        interceptor(CoordinatorGrpcProperties.Role.DISCOVERY).interceptCall(call, new Metadata(), handler());
        assertClosed(call, Status.Code.PERMISSION_DENIED);
    }

    @Test
    void shouldRejectUnknownUriInsteadOfFallingBackToCertificateCn() throws Exception {
        var call = call(certificate("spiffe://serialx/unlisted"));
        interceptor(CoordinatorGrpcProperties.Role.NODE).interceptCall(call, new Metadata(), handler());
        assertClosed(call, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void shouldInstallBoundIdentityAndServerDeadline() throws Exception {
        var call = call(certificate("spiffe://serialx/node-a"));
        ServerCallHandler<RegisterRequest, RegisterResponse> handler = (accepted, metadata) -> {
            assertEquals("node-a", CoordinatorRpcInterceptor.ACTOR.get().instanceId());
            assertNotNull(io.grpc.Context.current().getDeadline());
            return new ServerCall.Listener<>() { };
        };
        var listener = interceptor(CoordinatorGrpcProperties.Role.NODE).interceptCall(call, new Metadata(), handler);
        listener.onCancel();
        assertClosed(call, Status.Code.CANCELLED);
    }

    private CoordinatorRpcInterceptor interceptor(CoordinatorGrpcProperties.Role role) {
        var properties = new CoordinatorGrpcProperties();
        var identity = new CoordinatorGrpcProperties.MachineIdentity();
        identity.setCertificateUri("spiffe://serialx/node-a");
        identity.setInstanceId("node-a");
        identity.setRoles(Set.of(role));
        properties.setIdentities(Map.of("node", identity));
        return new CoordinatorRpcInterceptor(properties, deadlines);
    }
    private static SSLSession certificate(String uri) throws Exception {
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(6, uri)));
        var ssl = mock(SSLSession.class);
        when(ssl.getPeerCertificates()).thenReturn(new Certificate[]{certificate});
        return ssl;
    }
    @SuppressWarnings("unchecked")
    private static ServerCall<RegisterRequest, RegisterResponse> call(SSLSession ssl) {
        ServerCall<RegisterRequest, RegisterResponse> call = mock(ServerCall.class);
        Attributes.Builder attributes = Attributes.newBuilder();
        if (ssl != null) attributes.set(Grpc.TRANSPORT_ATTR_SSL_SESSION, ssl);
        when(call.getAttributes()).thenReturn(attributes.build());
        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<RegisterRequest, RegisterResponse>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName("serialx.coordination.v1.Coordinator/Register")
                .setRequestMarshaller(ProtoUtils.marshaller(RegisterRequest.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(RegisterResponse.getDefaultInstance())).build());
        return call;
    }
    private static ServerCallHandler<RegisterRequest, RegisterResponse> handler() { return (call, metadata) -> new ServerCall.Listener<>() { }; }
    private static void assertClosed(ServerCall<RegisterRequest, RegisterResponse> call, Status.Code expected) {
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(call).close(status.capture(), any(Metadata.class));
        assertEquals(expected, status.getValue().getCode());
    }
}
