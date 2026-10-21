package com.ronan.serialx.coordinator.application;

import static com.ronan.serialx.common.node.NodeTiming.LEASE_DURATION;

import com.ronan.serialx.common.util.Assert;
import com.ronan.serialx.coordinator.config.ServiceNodeAllocationProperties;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.*;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.infra.entity.SnowflakeNodeDO;
import com.ronan.serialx.infra.entity.SnowflakeNodeSessionDO;
import com.ronan.serialx.infra.mapper.SnowflakeNodeMapper;
import com.ronan.serialx.infra.mapper.SnowflakeNodeSessionMapper;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * 控制面节点分配，所有状态变更使用同一权威主库，事务提交后才返回租约。
 * 池锁串行化注册、续租和释放，避免并发幂等及所有权竞争。
 * 节点历史水位永久保留，新会话下界严格大于旧水位，终态会话不能复活。
 * 本类不负责数据面停止发号或定时续租。
 */
@Slf4j
public class SnowflakeNodeManager implements NodeAllocationGateway {
    /**
     * 活动态，还需满足租期未结束且仍拥有节点。
     */
    private static final String ACTIVE = "ACTIVE";
    /**
     * 租期结束的终态，不能通过重新注册复活。
     */
    private static final String EXPIRED = "EXPIRED";
    /**
     * 主动释放的终态，允许重复释放但不能再次注册。
     */
    private static final String RELEASED = "RELEASED";
    /**
     * 节点池及永久时间水位的持久化入口。
     */
    private final SnowflakeNodeMapper nodes;
    /**
     * 会话状态及注册幂等记录的持久化入口。
     */
    private final SnowflakeNodeSessionMapper sessions;
    /**
     * 节点接入与预留窗口配置；租期由共享系统策略确定。
     */
    private final ServiceNodeAllocationProperties properties;
    /**
     * HTTP 和进程内调用共用的参数校验器。
     */
    private final Validator validator;
    /**
     * 显式事务用于在抛出租约异常前提交终态，并在唯一键竞争回滚后开启全新重试事务。
     * 方法级 {@code @Transactional} 无法直接表达这两个“先结束事务、再继续处理”的边界。
     */
    private final TransactionTemplate transaction;

    /**
     * 装配依赖并校验配置；独立读已提交事务的 5 秒超时限制控制面锁等待。
     */
    public SnowflakeNodeManager(SnowflakeNodeMapper nodes, SnowflakeNodeSessionMapper sessions,
                                ServiceNodeAllocationProperties properties, PlatformTransactionManager transactions,
                                Validator validator) {
        properties.validate();
        this.nodes = nodes;
        this.sessions = sessions;
        this.properties = properties;
        this.validator = validator;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    /**
     * {@inheritDoc} 重复键竞争只在原事务回滚后用新事务读取已提交会话。
     */
    @Override
    public NodeLease register(NodeRegisterRequest request) {
        validate(request);
        log.debug("协调节点注册开始，runtimeId={}", request.getRuntimeId());
        NodeLease lease;
        try {
            lease = transaction.execute(status -> registerInTransaction(request));
        } catch (DuplicateKeyException ex) {
            // 原事务已回滚；用新事务读取并发请求已经提交的会话。
            log.debug("协调节点注册发生并发竞争，回滚后重查会话，runtimeId={}", request.getRuntimeId());
            lease = transaction.execute(status -> registerInTransaction(request));
        }
        // 事务外报告失败，保证已写入的 EXPIRED 状态可以提交。
        Assert.isNull(lease, () -> new NodeAllocationException(BusinessErrorCode.NODE_LEASE_LOST, 409));
        log.info("协调节点注册已提交，runtimeId={}, datacenterId={}, workerId={}, leaseExpireAtMs={}, reservedUntilMs={}", lease.getRuntimeId(), lease.getDatacenterId(), lease.getWorkerId(), lease.getLeaseExpireAtMs(), lease.getReservedUntilMs());
        return lease;
    }

    /**
     * 持池锁完成幂等检查和安全分配；有效会话重复注册不延长租期，null 表示失效。
     */
    private NodeLease registerInTransaction(NodeRegisterRequest request) {
        lockPool();
        // 先拿锁再采样主库时间，避免排队前的旧时间导致租期误判。
        long now = nodes.currentTimeMillis();
        SnowflakeNodeSessionDO existing = sessions.lockSession(request.getRuntimeId());
        if (existing != null) {
            if (!existing.getInstanceId().equals(request.getInstanceId()) || !isActive(existing, now)) return null;
            SnowflakeNodeDO node = nodes.lockNode(existing.getDatacenterId(), existing.getWorkerId());
            return owns(node, existing) && node.getLeaseExpireAtMs() > now ? lease(existing, now) : null;
        }
        long until = timeUpperBound(now, properties.getReservedWindow().toMillis());
        long expires = timeUpperBound(now, LEASE_DURATION.toMillis());
        SnowflakeNodeDO node = nodes.lockAvailable(now);
        if (node == null) throw new NodeAllocationException(BusinessErrorCode.NODE_POOL_EXHAUSTED, 503);
        if (node.getOwnerRuntimeId() != null) {
            SnowflakeNodeSessionDO previous = sessions.lockSession(node.getOwnerRuntimeId());
            if (previous != null && ACTIVE.equals(previous.getStatus())) {
                previous.setStatus(EXPIRED);
                sessions.update(previous);
            }
        }
        var session = new SnowflakeNodeSessionDO();
        session.setRuntimeId(request.getRuntimeId());
        session.setInstanceId(request.getInstanceId());
        session.setDatacenterId(node.getDatacenterId());
        session.setWorkerId(node.getWorkerId());
        // 新下界严格越过上任预留上界，防止跨会话复用同一时间编码。
        session.setValidFromMs(Math.max(SnowflakeGenerator.EPOCH, node.getReservedUntilMs() + 1));
        session.setReservedUntilMs(until);
        session.setLeaseExpireAtMs(expires);
        session.setStatus(ACTIVE);
        if (nodes.assign(session) != 1 || sessions.insert(session) != 1) throw lost();
        return lease(session, now);
    }

    /**
     * {@inheritDoc} 同时校验会话状态、节点坐标和当前所有者。
     */
    @Override
    public NodeLease renew(NodeIdentityRequest request) {
        return renew(request, null);
    }

    /**
     * mTLS 调用必须核对证书授权实例；null 仅供受信的进程内及迁移期 HTTP 调用。
     */
    public NodeLease renew(NodeIdentityRequest request, String authorizedInstanceId) {
        validate(request);
        log.debug("协调节点续租开始，runtimeId={}, datacenterId={}, workerId={}",
                request.runtimeId(), request.datacenterId(), request.workerId());
        NodeLease result = transaction.execute(status -> {
            lockPool();
            long now = nodes.currentTimeMillis();
            SnowflakeNodeSessionDO session = sessions.lockSession(request.runtimeId());
            authorizeInstance(session, authorizedInstanceId);
            if (!matches(session, request) || !isActive(session, now)) return null;
            SnowflakeNodeDO node = nodes.lockNode(request.datacenterId(), request.workerId());
            if (!owns(node, session) || node.getLeaseExpireAtMs() <= now) return null;
            session.setReservedUntilMs(Math.max(session.getReservedUntilMs(),
                    timeUpperBound(now, properties.getReservedWindow().toMillis())));
            session.setLeaseExpireAtMs(Math.max(session.getLeaseExpireAtMs(),
                    timeUpperBound(now, LEASE_DURATION.toMillis())));
            if (nodes.renew(session) != 1) throw lost();
            sessions.update(session);
            return lease(session, now);
        });
        // 提交后再抛异常，避免回滚刚持久化的过期终态。
        if (result == null) throw lost();
        log.debug("协调节点续租已提交，runtimeId={}, leaseExpireAtMs={}, reservedUntilMs={}",
                result.getRuntimeId(), result.getLeaseExpireAtMs(), result.getReservedUntilMs());
        return result;
    }

    /**
     * {@inheritDoc} 保留历史水位，不干扰后续接管节点的新会话。
     */
    @Override
    public void release(NodeIdentityRequest request) {
        release(request, null);
    }

    /**
     * 在同一事务的会话锁下核对实例身份，旧进程及其他实例不能释放该会话。
     */
    public void release(NodeIdentityRequest request, String authorizedInstanceId) {
        validate(request);
        log.debug("协调节点释放开始，runtimeId={}, datacenterId={}, workerId={}",
                request.runtimeId(), request.datacenterId(), request.workerId());
        Boolean released = transaction.execute(status -> {
            lockPool();
            SnowflakeNodeSessionDO session = sessions.lockSession(request.runtimeId());
            authorizeInstance(session, authorizedInstanceId);
            if (!matches(session, request)) return false;
            // 重复释放不再触碰节点，它可能已由新会话接管。
            if (RELEASED.equals(session.getStatus())) return true;
            if (!owns(nodes.lockNode(request.datacenterId(), request.workerId()), session)) return false;
            if (nodes.release(session) != 1) throw lost();
            session.setStatus(RELEASED);
            sessions.update(session);
            return true;
        });
        if (!Boolean.TRUE.equals(released)) throw lost();
        log.info("协调节点释放已提交，runtimeId={}, datacenterId={}, workerId={}",
                request.runtimeId(), request.datacenterId(), request.workerId());
    }

    /**
     * 已知会话仅允许其绑定的证书实例操作；不泄露会话或实例详情。
     */
    private void authorizeInstance(SnowflakeNodeSessionDO session, String authorizedInstanceId) {
        if (authorizedInstanceId != null && (session == null
                || !authorizedInstanceId.equals(session.getInstanceId()))) {
            throw new NodeAllocationException(BusinessErrorCode.NODE_AUTH_FAILED, 403);
        }
    }

    /**
     * 所有节点操作先取得池锁，再访问会话与节点；固定锁行缺失时禁止分配。
     */
    private void lockPool() {
        // shortcut: 分配/续租统一锁定 0/0 行；控制面锁等待成为瓶颈时改为分槽锁并验证并发幂等。
        if (nodes.lockPool() == null) throw new NodeAllocationException(BusinessErrorCode.NODE_POOL_EXHAUSTED, 503);
    }

    /**
     * 检查活动态及主库租期；过期时写入终态，由外层事务提交后再报告失败。
     */
    private boolean isActive(SnowflakeNodeSessionDO session, long now) {
        if (!ACTIVE.equals(session.getStatus())) return false;
        if (session.getLeaseExpireAtMs() <= now) {
            session.setStatus(EXPIRED);
            sessions.update(session);
            return false;
        }
        return true;
    }

    /**
     * UUID 已用于查找会话，此处补充校验节点的两部分坐标。
     */
    private boolean matches(SnowflakeNodeSessionDO session, NodeIdentityRequest request) {
        return session != null && session.getDatacenterId() == request.datacenterId()
                && session.getWorkerId() == request.workerId();
    }

    /**
     * 以运行 UUID 判断当前所有者，防止旧会话操作已重新分配的节点。
     */
    private boolean owns(SnowflakeNodeDO node, SnowflakeNodeSessionDO session) {
        return node != null && Objects.equals(node.getOwnerRuntimeId(), session.getRuntimeId());
    }

    /**
     * 校验纪元、41 位时间容量和加法边界，再计算包含边界的时间上界。
     */
    private long timeUpperBound(long now, long duration) {
        long max = SnowflakeGenerator.EPOCH + (1L << 41) - 1;
        if (now < SnowflakeGenerator.EPOCH || now > max || duration <= 0 || duration > max - now) {
            throw new NodeAllocationException(BusinessErrorCode.NODE_TIME_WINDOW_EXHAUSTED, 503);
        }
        return now + duration;
    }

    /**
     * 返回主库采样时刻的剩余租期，注册重试不能重新取得完整初始租期。
     */
    private NodeLease lease(SnowflakeNodeSessionDO session, long now) {
        var lease = new NodeLease();
        lease.setRuntimeId(session.getRuntimeId());
        lease.setDatacenterId(session.getDatacenterId());
        lease.setWorkerId(session.getWorkerId());
        lease.setValidFromMs(session.getValidFromMs());
        lease.setReservedUntilMs(session.getReservedUntilMs());
        lease.setLeaseExpireAtMs(session.getLeaseExpireAtMs());
        lease.setLeaseDurationMs(session.getLeaseExpireAtMs() - now);
        lease.setEpoch(SnowflakeGenerator.EPOCH);
        lease.setWorkerIdBits(5);
        lease.setDatacenterIdBits(5);
        lease.setSequenceBits(12);
        return lease;
    }

    /**
     * 进程内调用同样经过 Bean Validation，不能仅依赖控制器校验。
     */
    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new NodeAllocationException(BusinessErrorCode.NODE_PARAM_INVALID, 400);
        }
    }

    /**
     * 统一表达会话失效或所有权不匹配，内部接口返回 HTTP 409。
     */
    private NodeAllocationException lost() {
        return new NodeAllocationException(BusinessErrorCode.NODE_LEASE_LOST, 409);
    }
}
