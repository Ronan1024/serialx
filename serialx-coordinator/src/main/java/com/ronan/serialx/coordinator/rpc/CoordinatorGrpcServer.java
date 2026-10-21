package com.ronan.serialx.coordinator.rpc;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** admin 内装配的协调端口，不提供第二个启动程序，也不与后台 HTTP 共用业务线程池。 */
@Component
public class CoordinatorGrpcServer implements SmartLifecycle {
    private final CoordinatorGrpcProperties properties;
    private final CoordinatorRpcService service;
    private volatile boolean running;
    private Server server;
    private ThreadPoolExecutor requests;
    private ScheduledThreadPoolExecutor deadlines;

    public CoordinatorGrpcServer(CoordinatorGrpcProperties properties, CoordinatorRpcService service) {
        this.properties = properties;
        this.service = service;
    }

    /** Spring 完成 Bean/Flyway 初始化后才开放端口；TLS 配置错误直接使启动失败。 */
    @Override
    public synchronized void start() {
        if (running || !properties.isEnabled()) return;
        properties.validate();
        // 执行器饱和会拒绝提交并由 gRPC 终止相关调用，不能承诺这一传输层路径总映射为
        // RESOURCE_EXHAUSTED；已进入业务处理的并发预算耗尽才有明确的该状态。
        requests = new ThreadPoolExecutor(properties.getWorkerThreads(), properties.getWorkerThreads(),
                0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                threads("serialx-coordinator-rpc-"), new ThreadPoolExecutor.AbortPolicy());
        deadlines = new ScheduledThreadPoolExecutor(1, threads("serialx-coordinator-deadline-"));
        deadlines.setRemoveOnCancelPolicy(true);
        try {
            server = NettyServerBuilder.forAddress(new InetSocketAddress(properties.getBindAddress(), properties.getPort()))
                    .executor(requests).maxInboundMessageSize(properties.getMaxInboundMessageSize())
                    .sslContext(GrpcSslContexts.forServer(properties.getCertificateChain().toFile(), properties.getPrivateKey().toFile())
                            .trustManager(properties.getTrustCertificate().toFile()).clientAuth(ClientAuth.REQUIRE).build())
                    .addService(ServerInterceptors.intercept(service, new CoordinatorRpcInterceptor(properties, deadlines)))
                    .build().start();
            running = true;
        } catch (IOException | RuntimeException ex) {
            closeResources();
            throw new IllegalStateException("协调 gRPC 端口启动失败，请检查端口和 TLS 配置", ex);
        }
    }

    /** 停止网络接入不等价于 Release，不能让 admin 停机导致节点提前复用。 */
    @Override
    public synchronized void stop() {
        running = false;
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(properties.getShutdownGrace().toMillis(), TimeUnit.MILLISECONDS)) server.shutdownNow();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
        closeResources();
    }

    @Override public void stop(Runnable callback) { try { stop(); } finally { callback.run(); } }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }

    private void closeResources() {
        if (server != null) { server.shutdownNow(); server = null; }
        if (requests != null) { requests.shutdownNow(); requests = null; }
        if (deadlines != null) { deadlines.shutdownNow(); deadlines = null; }
    }

    private static ThreadFactory threads(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
