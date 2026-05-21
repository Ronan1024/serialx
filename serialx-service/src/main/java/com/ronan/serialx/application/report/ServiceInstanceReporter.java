package com.ronan.serialx.application.report;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ronan.serialx.adapter.redis.ServiceInstanceStatusWriterImpl;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.domain.model.ServiceInstanceInfo;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import com.ronan.serialx.support.ServiceInstanceIdentity;
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

    private volatile boolean running;

    private ServiceInstanceInfo instanceInfo;

    /**
     * 注册当前实例。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registeredInstance() throws UnknownHostException {
        if (!serviceInstanceProperties.isEnabled()) {
            log.info("service instance register disabled");
            return;
        }
        String instanceIp = serviceInstanceIdentity.getIp();
        String instanceId = serviceInstanceIdentity.getInstanceId();
        String hostName = InetAddress.getLocalHost().getHostName();
        String runtimeId = UUID.randomUUID().toString();
        checkInstanceConflict(instanceId, runtimeId);
        LocalDateTime now = LocalDateTime.now();
        instanceInfo = new ServiceInstanceInfo();
        instanceInfo.setInstanceId(instanceId);
        instanceInfo.setHostName(hostName);
        instanceInfo.setCreateTime(now);
        instanceInfo.setLastReportTime(now);
        instanceInfo.setIp(instanceIp);
        instanceInfo.setPort(serviceInstanceIdentity.getPort());
        instanceInfo.setRuntimeId(runtimeId);
        instanceInfo.setStatus(ServiceInstanceRedisConstants.ACTIVE);
        running = true;
        reportStatus();
    }

    /**
     * 定时上报节点心跳与 Namespace 加载状态。
     */
    @Scheduled(fixedDelayString = "#{@serviceInstanceProperties.reportInterval.toMillis()}")
    public void scheduledReportStatus() {
        if (!running || !serviceInstanceProperties.isEnabled()) {
            return;
        }
        reportStatus();
    }

    private void reportStatus() {
        if (instanceInfo == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        instanceInfo.setStatus(ServiceInstanceRedisConstants.ACTIVE);
        instanceInfo.setLastReportTime(now);
        statusWriter.saveOnline(instanceInfo);
        namespaceRegistry.listAll().forEach(runtime -> reportNamespaceRuntime(runtime, now));
    }

    private void reportNamespaceRuntime(NamespaceRuntime runtime, LocalDateTime reportTime) {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(instanceInfo.getInstanceId());
        status.setHost(serviceInstanceIdentity.getHost());
        status.setNamespaceCode(runtime.getNamespaceCode());
        status.setLoadedVersion(runtime.getVersion());
        status.setRuntimeStatus(ServiceInstanceRedisConstants.ACTIVE);
        status.setLastReportTime(reportTime);
        statusWriter.saveOrUpdate(status);
    }

    @Override
    public void start() {
        running = instanceInfo != null;
    }

    @Override
    public void stop() {
        if (instanceInfo == null) {
            running = false;
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        instanceInfo.setStatus(ServiceInstanceRedisConstants.OFFLINE);
        instanceInfo.setLastOfflineTime(now);
        instanceInfo.setLastReportTime(now);
        statusWriter.saveOffline(instanceInfo);
        namespaceRegistry.listAll().forEach(runtime -> reportNamespaceOffline(runtime, now));
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void checkInstanceConflict(String instanceId, String runtimeId) {
        if (!statusWriter.tryAcquireHeartbeat(instanceId, runtimeId)) {
            throw new BizException(
                    "SERVICE_INSTANCE_CONFLICT",
                    "实例 ID 已被在线节点占用，请更换 serialx.service.instance.instance-id 或等待旧心跳过期");
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
