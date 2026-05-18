package com.ronan.serialx.application.report;

import com.ronan.serialx.common.util.Assert;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import com.ronan.serialx.service.namespace.NamespaceRegistry;
import com.ronan.serialx.service.namespace.NamespaceRuntime;
import com.ronan.serialx.service.namespace.ServiceInstanceStatusWriterImpl;
import com.ronan.serialx.utils.IpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

/**
 * @program: serialx
 * @description:
 * @author: L.J.Ran
 * @create: 2026/5/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceInstanceReporter implements SmartLifecycle {

    private final ServiceInstanceProperties serviceInstanceProperties;
    private final ServiceInstanceStatusWriterImpl serviceInstanceStatusWriter;
    private final NamespaceRegistry namespaceRegistry;
    private volatile boolean running = false;

    @Value("${server.port}")
    private String port;

    /**
     * 定时上报实例状态。
     */
    @Scheduled(fixedDelayString = "${serialx.service.instance.report-interval:20s}")
    public void scheduledReportStatus() {
        if (!running || !StringUtils.hasText(serviceInstanceProperties.getInstanceId())) {
            return;
        }
        try {
            reportCurrentStatus();
        } catch (UnknownHostException ex) {
            log.warn("report service instance status failed, instanceId={}", serviceInstanceProperties.getInstanceId(), ex);
        }
    }

    /**
     * 注册当前实例
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registeredInstance() throws UnknownHostException {
        Assert.isFalse(!StringUtils.hasText(serviceInstanceProperties.getInstanceId()), () -> new RuntimeException("实例 ID 未配置"));
        running = true;
        reportCurrentStatus();
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void reportCurrentStatus() throws UnknownHostException {
        serviceInstanceStatusWriter.saveOrUpdate(buildStatus(null));
        for (NamespaceRuntime runtime : namespaceRegistry.listAll()) {
            serviceInstanceStatusWriter.saveOrUpdate(buildStatus(runtime));
        }
    }

    private ServiceInstanceConfigStatusDO buildStatus(NamespaceRuntime runtime) throws UnknownHostException {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(serviceInstanceProperties.getInstanceId());
        status.setHost(InetAddress.getLocalHost().getHostName() + "/" + IpUtil.getLocalIp() + ":" + port);
        if (runtime != null) {
            status.setNamespaceCode(runtime.getNamespaceCode());
            status.setLoadedVersion(runtime.getVersion());
        }
        status.setRuntimeStatus("ACTIVE");
        status.setLastReportTime(LocalDateTime.now());
        return status;
    }
}
