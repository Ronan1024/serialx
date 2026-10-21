package com.ronan.serialx.application.node;

import static com.ronan.serialx.common.node.NodeTiming.LEASE_DURATION;
import static com.ronan.serialx.common.node.NodeTiming.RENEW_INTERVAL_SECONDS;
import static com.ronan.serialx.common.node.NodeTiming.SAFETY_MARGIN;
import static com.ronan.serialx.core.snowflake.SnowflakeGenerator.DATACENTER_ID_BITS;
import static com.ronan.serialx.core.snowflake.SnowflakeGenerator.EPOCH;
import static com.ronan.serialx.core.snowflake.SnowflakeGenerator.MAX_DATACENTER_ID;
import static com.ronan.serialx.core.snowflake.SnowflakeGenerator.MAX_TIMESTAMP;
import static com.ronan.serialx.core.snowflake.SnowflakeGenerator.MAX_WORKER_ID;
import static com.ronan.serialx.core.snowflake.SnowflakeGenerator.SEQUENCE_BITS;
import static com.ronan.serialx.core.snowflake.SnowflakeGenerator.WORKER_ID_BITS;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.NodeAllocationException;
import com.ronan.serialx.common.node.NodeAllocationGateway;
import com.ronan.serialx.common.node.NodeIdentityRequest;
import com.ronan.serialx.common.node.NodeLease;
import com.ronan.serialx.common.node.NodeRegisterRequest;
import com.ronan.serialx.config.SnowflakeNodeProperties;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.infra.http.OutboundHttpProperties;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理实例级雪花节点的注册、续租、发号授权及关闭，不负责 namespace 配置加载。
 * 注册成功不等于可发号，首次配置加载完成后才进入 READY；失效后仅允许关闭清理。
 * 租约预算使用单调时钟，发号窗口使用墙上时间，两类约束均满足时才允许发号。
 * IO 不持有状态锁；关闭与发号统一按生成器锁、状态锁的顺序同步，避免锁顺序反转。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOnDatabaseInitialization
public class SnowflakeNodeLifecycle implements AutoCloseable {
    /**
     * 节点分配入口，屏蔽远程控制面与进程内实现的调用差异。
     */
    private final NodeAllocationGateway gateway;

    /**
     * 节点启动策略，限定注册失败时的最大尝试次数。
     */
    private final SnowflakeNodeProperties properties;

    /**
     * 统一出站请求预算；进程内 Gateway 同样使用该预算限制调用方等待。
     */
    private final OutboundHttpProperties outboundHttpProperties;

    /**
     * 当前进程的注册身份；重试复用同一 runtimeId，避免重复分配会话。
     */
    private final ServiceInstanceIdentity identity;

    /**
     * 分别提供 Unix 毫秒时间和单调纳秒时间，隔离时钟来源并支持边界测试。
     */
    private final NodeClock clock;

    /**
     * 保护租约、时间水位及生命周期状态的一致性；网络 IO 不占用此锁。
     */
    private final Object guard = new Object();

    /**
     * 防止并发续租覆盖状态；独立于状态锁，避免续租 IO 阻塞发号检查。
     */
    private final ReentrantLock renewalLock = new ReentrantLock();

    /**
     * 注册、续租及释放使用的有界 IO 池，与续租调度线程隔离；由 Spring 统一关闭。
     */
    private final ExecutorService nodeRequestExecutor;

    /**
     * 专用续租调度器，避免普通定时任务挤占续租时机；由 Spring 统一关闭。
     */
    private final ScheduledExecutorService nodeRenewScheduler;

    /**
     * 注册成功后发布的实例级共享生成器；volatile 保证引用可见性，发号互斥由生成器自身负责。
     */
    private volatile SnowflakeGenerator generator;

    /**
     * 最近一次通过校验的主库租约，注册完成前为空；在 guard 内读取和替换。
     */
    private NodeLease lease;

    /**
     * 本地单调时钟截止点（纳秒），由请求发送时刻加剩余租期并扣除安全余量得到，不是 Unix 时间戳。
     */
    private long deadlineNanos;

    /**
     * 已接受的墙上时间水位（Unix 毫秒），用于检测时钟回拨；-1 表示尚未采样。
     */
    private long lastWall = -1;

    /**
     * 生命周期状态在 guard 内读写；启动就绪不替代实时租约检查，关闭状态不能被迟到响应覆盖。
     */
    private LifecycleState state = LifecycleState.STARTING;

    /**
     * 当前节点的续租任务句柄，在 guard 内保存和取得；关闭时仅取消该任务，不关闭共享调度器。
     */
    private ScheduledFuture<?> renewalTask;

    /**
     * 在 Spring 初始化阶段注册租约并装配共享生成器，成功后仍保持 STARTING。
     * 仅可恢复错误允许在次数上限内重试；中断或终态错误立即停止，最终失败时关闭清理。
     * 首次 namespace 加载完成后，由启动编排调用 {@link #markReady()} 开放发号。
     *
     * @throws IllegalStateException 注册失败或启动被中断，原始失败原因保留在 cause 中
     */
    @PostConstruct
    public void initialize() {
        log.info("节点注册开始，runtimeId={}, maxAttempts={}", identity.getRuntimeId(), properties.getStartupAttempts());
        RuntimeException failure = null;
        for (int attempt = 0; attempt < properties.getStartupAttempts(); attempt++) {
            log.debug("节点注册尝试，runtimeId={}, attempt={}", identity.getRuntimeId(), attempt + 1);
            long sent = clock.nanoTime();
            try {
                NodeRegisterRequest nodeRegisterRequest = new NodeRegisterRequest(identity.getRuntimeId(), identity.getInstanceId());
                NodeLease reply = call(() -> gateway.register(nodeRegisterRequest));
                synchronized (guard) {
                    validateLease(reply, null);
                    lease = reply;
                    deadlineNanos = deadline(reply, sent);
                    checkLease(false);
                    generator = new SnowflakeGenerator(reply.getWorkerId(), reply.getDatacenterId(), this::timestamp, () -> {
                        synchronized (guard) {
                            checkLease(true);
                        }
                    });
                    renewalTask = nodeRenewScheduler.scheduleWithFixedDelay(this::renew, RENEW_INTERVAL_SECONDS, RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
                    log.info("节点注册成功，runtimeId={}, datacenterId={}, workerId={}, leaseDurationMs={}, state={}", identity.getRuntimeId(), reply.getDatacenterId(), reply.getWorkerId(), reply.getLeaseDurationMs(), state);
                    log.info("节点续租调度已启动，runtimeId={}, intervalSeconds={}", identity.getRuntimeId(), RENEW_INTERVAL_SECONDS);
                }
                return;
            } catch (RuntimeException ex) {
                failure = ex;
                boolean interrupted = Thread.currentThread().isInterrupted();
                if (interrupted) {
                    failure = new IllegalStateException("节点注册被中断");
                }
                synchronized (guard) {
                    log.warn("节点注册尝试失败，runtimeId={}, attempt={}, state={}, exceptionType={}", identity.getRuntimeId(), attempt + 1, state, ex.getClass().getSimpleName());
                    if (interrupted || state == LifecycleState.LOST || state == LifecycleState.CLOSED || !retryable(ex)) {
                        break;
                    }
                }
            }
        }
        log.error("节点注册最终失败，runtimeId={}，关闭节点并终止启动", identity.getRuntimeId());
        close();
        throw new IllegalStateException("节点注册失败", failure);
    }

    /**
     * 配置加载完成后才允许发号；过期或关闭的节点不能重新开放。
     *
     * @throws NodeAllocationException 尚未取得有效租约、节点已失效或已关闭
     */
    void markReady() {
        synchronized (guard) {
            checkLease(false);
            if (state != LifecycleState.READY) {
                log.info("节点发号就绪，runtimeId={}, previousState={}, state=READY", identity.getRuntimeId(), state);
            }
            state = LifecycleState.READY;
        }
    }

    /**
     * 实时检查本地预算和绝对时间窗口，不依赖 Redis 展示快照。
     * 检测到过期、窗口越界或时钟回拨时会标记 LOST，并非无副作用的状态读取。
     *
     * @return 当前检查时刻是否满足发号条件，不保证后续调用时租约仍有效
     */
    public boolean isAvailable() {
        synchronized (guard) {
            try {
                checkLease(true);
                return true;
            } catch (NodeAllocationException ex) {
                return false;
            }
        }
    }

    /**
     * 返回实例级共享生成器，namespace 刷新不更换该对象。
     * 取得引用不代表节点就绪，每次发号仍由生成器回调检查实时授权。
     *
     * @return 注册成功后创建的生成器
     * @throws NodeAllocationException 生成器尚未完成装配
     */
    public SnowflakeGenerator generator() {
        SnowflakeGenerator current = generator;
        if (current == null) {
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        }
        return current;
    }

    /**
     * 同时最多执行一次续租，并发触发时直接跳过，不排队积压续租请求。
     * IO 期间保留原授权，响应提交前再次检查旧租约；迟到响应不能复活失效或关闭节点。
     * 可恢复错误不延长预算，其他错误使节点失效；异常由本方法处理，不中断后续调度。
     */
    public void renew() {
        if (!renewalLock.tryLock()) {
            log.debug("跳过并发节点续租，runtimeId={}", identity.getRuntimeId());
            return;
        }
        try {
            NodeLease previous;
            long sent;
            synchronized (guard) {
                checkLease(false);
                previous = lease;
                sent = clock.nanoTime();
            }
            log.debug("节点续租开始，runtimeId={}, datacenterId={}, workerId={}",
                    previous.getRuntimeId(), previous.getDatacenterId(), previous.getWorkerId());
            NodeLease reply = call(() -> gateway.renew(new NodeIdentityRequest(previous.getRuntimeId(),
                    previous.getDatacenterId(), previous.getWorkerId())));
            synchronized (guard) {
                // 只能在旧授权仍有效时接纳新授权，不能用迟到响应跨过无租约区间。
                checkLease(false);
                validateLease(reply, previous);
                long nextDeadline = deadline(reply, sent);
                if (nextDeadline - clock.nanoTime() <= 0) {
                    throw fail(BusinessErrorCode.NODE_LEASE_LOST);
                }
                lease = reply;
                // 截止时间和预留窗口均不能被较旧响应缩小。
                if (nextDeadline - deadlineNanos > 0) {
                    deadlineNanos = nextDeadline;
                }
                log.debug("节点续租成功，runtimeId={}, datacenterId={}, workerId={}, leaseDurationMs={}, reservedUntilMs={}",
                        reply.getRuntimeId(), reply.getDatacenterId(), reply.getWorkerId(), reply.getLeaseDurationMs(), reply.getReservedUntilMs());
            }
        } catch (RuntimeException ex) {
            synchronized (guard) {
                if (state != LifecycleState.CLOSED && !retryable(ex)) {
                    if (state != LifecycleState.LOST) {
                        log.warn("节点租约失效，runtimeId={}, previousState={}, exceptionType={}",
                                identity.getRuntimeId(), state, ex.getClass().getSimpleName());
                    }
                    state = LifecycleState.LOST;
                }
                try {
                    checkLease(false);
                } catch (NodeAllocationException ignored) {
                    // 检查已记录必要的失效状态，后台任务无需再次抛出同一异常。
                }
            }
            // 不记录响应、令牌或底层 HTTP 异常，避免认证信息泄露。
            log.warn("节点续租失败，维持原安全预算，runtimeId={}, exceptionType={}", identity.getRuntimeId(), ex.getClass().getSimpleName());
        } finally {
            renewalLock.unlock();
        }
    }

    /**
     * 发号使用的同一个时间样本在生成器同步区间内校验，不能先验一个时间再编码另一个。
     *
     * @return 通过租约检查的 Unix 毫秒时间，供生成器直接编码
     */
    private long timestamp() {
        synchronized (guard) {
            return checkLease(true);
        }
    }

    /**
     * 在 guard 内校验本地发号授权，并推进已接受的墙上时间水位。
     * 租期耗尽、窗口越界或时钟回拨会使节点不可恢复地失效；仅未就绪不会改变状态。
     *
     * @param requireReady 发号及可用性检查为 true；注册和续租检查为 false，允许配置加载期间维护租约
     * @return 本次通过检查的 Unix 毫秒时间
     * @throws NodeAllocationException 未取得租约、未就绪、已失效或已关闭
     */
    private long checkLease(boolean requireReady) {
        if (state == LifecycleState.CLOSED || state == LifecycleState.LOST) {
            throw new NodeAllocationException(BusinessErrorCode.NODE_LEASE_LOST, 503);
        }
        if (lease == null) {
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        }
        if (clock.nanoTime() - deadlineNanos >= 0) {
            throw fail(BusinessErrorCode.NODE_LEASE_LOST);
        }
        long now = clock.wallTimeMillis();
        if (now < lease.getValidFromMs() || now > lease.getReservedUntilMs() || now < lastWall) {
            throw fail(BusinessErrorCode.NODE_TIME_WINDOW_EXHAUSTED);
        }
        lastWall = now;
        if (requireReady && state != LifecycleState.READY) {
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        }
        return now;
    }

    /**
     * 租约响应是发号授权的信任边界，调用方须持有状态锁。
     * 非法授权使会话失效；首次注册尚未进入时间窗口时仍允许重试。
     *
     * @param reply    控制面返回的待校验租约，空响应按非法授权处理
     * @param previous 续租前的有效租约；首次注册时为 null
     */
    private void validateLease(NodeLease reply, NodeLease previous) {
        validateIdentity(reply);
        validateLayout(reply);
        validateTimeWindow(reply);
        if (previous == null) {
            validateRegistrationTime(reply);
        } else {
            validateRenewalConsistency(reply, previous);
        }
    }

    /**
     * 运行 UUID 隔离进程会话，节点坐标必须能由固定布局无损编码。
     */
    private void validateIdentity(NodeLease reply) {
        if (reply == null || !identity.getRuntimeId().equals(reply.getRuntimeId())
                || reply.getDatacenterId() < 0 || reply.getDatacenterId() > MAX_DATACENTER_ID
                || reply.getWorkerId() < 0 || reply.getWorkerId() > MAX_WORKER_ID) {
            throw fail(BusinessErrorCode.NODE_LEASE_LOST);
        }
    }

    /**
     * 控制面与本地必须使用相同的时间基准和位布局，否则不能安全解释租约。
     */
    private void validateLayout(NodeLease reply) {
        if (reply.getEpoch() != EPOCH || reply.getWorkerIdBits() != WORKER_ID_BITS
                || reply.getDatacenterIdBits() != DATACENTER_ID_BITS || reply.getSequenceBits() != SEQUENCE_BITS) {
            throw fail(BusinessErrorCode.NODE_LAYOUT_MISMATCH);
        }
    }

    /**
     * 授权窗口不得越过本地时间容量，剩余租期还须覆盖安全余量。
     */
    private void validateTimeWindow(NodeLease reply) {
        boolean invalidTimeWindow = reply.getValidFromMs() < EPOCH
                || reply.getValidFromMs() > reply.getReservedUntilMs()
                || reply.getReservedUntilMs() > MAX_TIMESTAMP
                || reply.getLeaseExpireAtMs() > reply.getReservedUntilMs();
        boolean invalidLeaseDuration = reply.getLeaseDurationMs() <= SAFETY_MARGIN.toMillis()
                || reply.getLeaseDurationMs() > LEASE_DURATION.toMillis();
        if (invalidTimeWindow || invalidLeaseDuration) {
            throw fail(BusinessErrorCode.NODE_LEASE_LOST);
        }
    }

    /**
     * 续租只能延长原会话授权，不能更换节点、移动起点或回退已确认的时间水位。
     */
    private void validateRenewalConsistency(NodeLease reply, NodeLease previous) {
        if (reply.getDatacenterId() != previous.getDatacenterId()
                || reply.getWorkerId() != previous.getWorkerId()
                || reply.getValidFromMs() != previous.getValidFromMs()
                || reply.getReservedUntilMs() < previous.getReservedUntilMs()
                || reply.getLeaseExpireAtMs() < previous.getLeaseExpireAtMs()) {
            throw fail(BusinessErrorCode.NODE_LEASE_LOST);
        }
    }

    /**
     * 本机时间尚未追上授权下界不代表身份失效，保留启动重试而不提前发号。
     */
    private void validateRegistrationTime(NodeLease reply) {
        if (clock.wallTimeMillis() < reply.getValidFromMs()) {
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        }
    }

    /**
     * 将主库采样的剩余租期换算为本地单调时钟截止点，并扣除安全余量。
     * 以请求发送前的时刻为起点，使排队和请求耗时自然消耗预算，而不是收到响应后重新计时。
     *
     * @param reply 已通过校验的租约，剩余租期单位为毫秒
     * @param sent  提交节点调用前取得的单调时钟样本，单位为纳秒
     * @return 单调时钟截止点（纳秒），不是剩余时长或 Unix 时间戳
     */
    private long deadline(NodeLease reply, long sent) {
        return sent + TimeUnit.MILLISECONDS.toNanos(reply.getLeaseDurationMs() - SAFETY_MARGIN.toMillis());
    }

    /**
     * 在 guard 内记录不可恢复失效并构造异常，不覆盖已经提交的 CLOSED 状态。
     *
     * @param code 本次安全检查失败的业务错误码
     * @return 交由调用方抛出的业务异常，携带本次校验的错误码
     */
    private NodeAllocationException fail(BusinessErrorCode code) {
        if (state != LifecycleState.CLOSED) {
            if (state != LifecycleState.LOST) {
                log.warn("节点租约失效，runtimeId={}, previousState={}, errorCode={}", identity.getRuntimeId(), state, code.getCode());
            }
            state = LifecycleState.LOST;
        }
        return new NodeAllocationException(code, 503);
    }

    /**
     * 仅将服务端暂未就绪或节点池暂时耗尽视为可恢复错误，身份、布局和租约错误不得重试恢复。
     * 本方法只分类异常，不执行重试，也不改变生命周期状态。
     *
     * @param ex 节点操作失败的异常
     * @return 是否为允许重试的服务端错误
     */
    private boolean retryable(RuntimeException ex) {
        return ex instanceof NodeAllocationException failure && failure.getHttpStatus() >= 500 && (failure.getCode().equals(BusinessErrorCode.NODE_NOT_READY.getCode()) || failure.getCode().equals(BusinessErrorCode.NODE_POOL_EXHAUSTED.getCode()));
    }

    /**
     * 在有界 IO 池执行节点操作，以统一请求预算限制调用方等待，调用方不得持有 guard。
     * 超时或中断会尝试取消任务，但不保证底层操作停止或主库事务回滚；注册重试必须复用 runtimeId。
     *
     * @param operation 注册、续租或释放操作
     * @param <T>       操作结果类型，释放操作可返回 null
     * @return 底层操作结果；底层运行时异常原样向上传递
     * @throws NodeAllocationException 请求被拒绝、等待超时、中断或发生非运行时执行异常
     */
    private <T> T call(Supplier<T> operation) {
        Future<T> pending;
        try {
            pending = nodeRequestExecutor.submit(operation::get);
        } catch (RejectedExecutionException ex) {
            log.debug("节点请求执行器已饱和或关闭，runtimeId={}", identity.getRuntimeId());
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        }
        try {
            return pending.get(outboundHttpProperties.getRequestTimeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.debug("节点请求等待被中断，runtimeId={}", identity.getRuntimeId());
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        } catch (TimeoutException ex) {
            log.debug("节点请求等待超时，runtimeId={}, timeoutMs={}", identity.getRuntimeId(), outboundHttpProperties.getRequestTimeout().toMillis());
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new NodeAllocationException(BusinessErrorCode.NODE_NOT_READY, 503);
        } finally {
            if (!pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    /**
     * 按生成器锁、guard 的顺序等待在途发号结束并提交 CLOSED，重复调用不重复清理。
     */
    @Override
    @PreDestroy
    public void close() {
        NodeLease release;
        ScheduledFuture<?> task;
        Object monitor = generator == null ? guard : generator;
        synchronized (monitor) {
            synchronized (guard) {
                if (state == LifecycleState.CLOSED) {
                    return;
                }
                log.info("节点关闭开始，runtimeId={}, previousState={}, state=CLOSED", identity.getRuntimeId(), state);
                state = LifecycleState.CLOSED;
                release = lease;
                task = renewalTask;
            }
        }
        if (task != null) {
            task.cancel(true);
            log.debug("节点续租任务已取消，runtimeId={}", identity.getRuntimeId());
        }
        if (release != null) {
            try {
                log.info("节点释放开始，runtimeId={}, datacenterId={}, workerId={}",
                        release.getRuntimeId(), release.getDatacenterId(), release.getWorkerId());
                call(() -> {
                    gateway.release(new NodeIdentityRequest(release.getRuntimeId(), release.getDatacenterId(),
                            release.getWorkerId()));
                    return null;
                });
                log.info("节点释放成功，runtimeId={}", release.getRuntimeId());
            } catch (RuntimeException ex) {
                log.warn("节点释放失败，保留主库历史水位，runtimeId={}, exceptionType={}",
                        release.getRuntimeId(), ex.getClass().getSimpleName());
            }
        }
    }

    /**
     * 实例生命周期：STARTING 可进入 READY、LOST 或 CLOSED，READY 可进入 LOST 或 CLOSED。
     * LOST 仅允许进入 CLOSED 完成清理；CLOSED 为最终状态，任何迟到结果都不能将其覆盖。
     */
    private enum LifecycleState {
        /**
         * 注册或首次 namespace 加载中，尚不可发号。
         */
        STARTING,
        /**
         * 启动就绪，发号仍须满足租约和时间窗口约束。
         */
        READY,
        /**
         * 租约失效，不可恢复发号，但仍需关闭清理。
         */
        LOST,
        /**
         * 关闭已开始，不表示主库租约释放成功。
         */
        CLOSED
    }
}
