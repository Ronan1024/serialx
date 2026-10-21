package com.ronan.serialx.application.report;

import static com.ronan.serialx.common.node.NodeTiming.HEARTBEAT_INTERVAL_SECONDS;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import com.ronan.serialx.adapter.redis.ServiceInstanceStatusWriterImpl;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.domain.model.ServiceInstanceInfo;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import com.ronan.serialx.application.node.SnowflakeNodeLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Service 实例注册与状态上报器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceInstanceReporter implements SmartLifecycle {

    private final ServiceInstanceProperties serviceInstanceProperties;

    private final ServiceInstanceIdentity serviceInstanceIdentity;

    private final ServiceInstanceStatusWriterImpl statusWriter;

    private final NamespaceRegistry namespaceRegistry;

    private final SnowflakeNodeLifecycle nodeLifecycle;

    private volatile boolean running;

    private ServiceInstanceInfo instanceInfo;

    /**
     * 注册当前实例。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registeredInstance() throws UnknownHostException {
        if (!serviceInstanceProperties.isEnabled()) {
            log.info("实例上报已禁用");
            return;
        }
        String instanceIp = serviceInstanceIdentity.getIp();
        String instanceId = serviceInstanceIdentity.getInstanceId();
        String hostName = InetAddress.getLocalHost().getHostName();
        String runtimeId = serviceInstanceIdentity.getRuntimeId();
        checkInstanceConflict(instanceId, runtimeId);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        instanceInfo = new ServiceInstanceInfo();
        instanceInfo.setInstanceId(instanceId);
        instanceInfo.setHostName(hostName);
        instanceInfo.setCreateTime(now);
        instanceInfo.setLastReportTime(now);
        instanceInfo.setIp(instanceIp);
        instanceInfo.setPort(serviceInstanceIdentity.getPort());
        instanceInfo.setRuntimeId(runtimeId);
        instanceInfo.setStatus(nodeLifecycle.isAvailable() ? ServiceInstanceRedisConstants.ACTIVE : "NODE_NOT_READY");
        running = true;
        log.info("实例上报已启用，instanceId={}, runtimeId={}, intervalSeconds={}", instanceId, runtimeId, HEARTBEAT_INTERVAL_SECONDS);
        safeReportStatus();
    }

    /**
     * 定时上报节点心跳与 Namespace 加载状态。
     */
    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    public void scheduledReportStatus() {
        if (!running || !serviceInstanceProperties.isEnabled()) {
            log.debug("跳过实例定时上报，running={}, enabled={}", running, serviceInstanceProperties.isEnabled());
            return;
        }
        safeReportStatus();
    }

    private void reportStatus() {
        if (instanceInfo == null) {
            return;
        }
        log.debug("实例状态上报开始，instanceId={}, runtimeId={}", instanceInfo.getInstanceId(), instanceInfo.getRuntimeId());
        LocalDateTime now = LocalDateTime.now();
        instanceInfo.setStatus(nodeLifecycle.isAvailable() ? ServiceInstanceRedisConstants.ACTIVE : "NODE_NOT_READY");
        instanceInfo.setLastReportTime(now);
        statusWriter.saveOnline(instanceInfo);
        namespaceRegistry.listAll().forEach(runtime -> reportNamespaceRuntime(runtime, now));
        log.debug("实例状态上报完成，instanceId={}, runtimeId={}, status={}, namespaceCount={}",
                instanceInfo.getInstanceId(), instanceInfo.getRuntimeId(), instanceInfo.getStatus(), namespaceRegistry.listAll().size());
    }

    private void reportNamespaceRuntime(NamespaceRuntime runtime, LocalDateTime reportTime) {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(instanceInfo.getInstanceId());
        status.setHost(serviceInstanceIdentity.getHost());
        status.setNamespaceCode(runtime.getNamespaceCode());
        status.setLoadedVersion(runtime.getVersion());
        status.setRuntimeStatus(runtime.getRuntimeStatus());
        status.setLastReportTime(reportTime);
        statusWriter.saveOrUpdate(status);
        log.debug("Namespace 状态上报完成，instanceId={}, runtimeId={}, namespaceCode={}, version={}, status={}",
                instanceInfo.getInstanceId(), instanceInfo.getRuntimeId(), runtime.getNamespaceCode(), runtime.getVersion(), runtime.getRuntimeStatus());
    }

    @Override
    public void start() {
        running = instanceInfo != null;
    }

    @Override
    public void stop() {
        // 先关闭发号并释放主库节点，再处理不可靠的 Redis 离线快照。
        nodeLifecycle.close();
        running = false;
        if (instanceInfo == null) {
            running = false;
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        instanceInfo.setStatus(ServiceInstanceRedisConstants.OFFLINE);
        instanceInfo.setLastOfflineTime(now);
        instanceInfo.setLastReportTime(now);
        try {
            statusWriter.saveOffline(instanceInfo);
            namespaceRegistry.listAll().forEach(runtime -> reportNamespaceOffline(runtime, now));
            log.info("实例离线状态上报完成，instanceId={}, runtimeId={}", instanceInfo.getInstanceId(), instanceInfo.getRuntimeId());
        } catch (RuntimeException ex) {
            log.warn("实例离线快照上报失败，发号已关闭，instanceId={}, runtimeId={}, exceptionType={}",
                    instanceInfo.getInstanceId(), instanceInfo.getRuntimeId(), ex.getClass().getSimpleName());
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void checkInstanceConflict(String instanceId, String runtimeId) {
        log.debug("实例心跳占用检查开始，instanceId={}, runtimeId={}", instanceId, runtimeId);
        boolean acquired;
        try {
            acquired = statusWriter.tryAcquireHeartbeat(instanceId, runtimeId);
        } catch (RuntimeException ex) {
            // 主库租约已确保雪花身份唯一；Redis 断连只影响展示身份冲突检查。
            log.warn("实例心跳检查不可用，节点唯一性仍由主库租约保护，instanceId={}, runtimeId={}, exceptionType={}",
                    instanceId, runtimeId, ex.getClass().getSimpleName());
            return;
        }
        if (!acquired) {
            log.error("实例心跳存在占用冲突，instanceId={}, runtimeId={}", instanceId, runtimeId);
            throw new BizException(
                    "SERVICE_INSTANCE_CONFLICT",
                    "实例 ID 已被在线节点占用，请更换 serialx.service.instance.instance-id 或等待旧心跳过期");
        }
        log.debug("实例心跳占用检查完成，instanceId={}, runtimeId={}", instanceId, runtimeId);
    }

    private void safeReportStatus() {
        try {
            reportStatus();
        } catch (RuntimeException ex) {
            log.warn("实例状态上报失败，保留本地主库租约状态，instanceId={}, runtimeId={}, exceptionType={}",
                    serviceInstanceIdentity.getInstanceId(), serviceInstanceIdentity.getRuntimeId(), ex.getClass().getSimpleName());
        }
    }

    private void reportNamespaceOffline(NamespaceRuntime runtime, LocalDateTime reportTime) {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(instanceInfo.getInstanceId());
        status.setHost(serviceInstanceIdentity.getHost());
        status.setNamespaceCode(runtime.getNamespaceCode());
        status.setLoadedVersion(runtime.getVersion());
        status.setRuntimeStatus(ServiceInstanceRedisConstants.OFFLINE);
        status.setLastReportTime(reportTime);
        status.setLastOfflineTime(reportTime);
        statusWriter.saveOrUpdate(status);
    }
}
