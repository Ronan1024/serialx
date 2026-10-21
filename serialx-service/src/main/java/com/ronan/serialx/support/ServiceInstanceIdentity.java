package com.ronan.serialx.support;

import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.utils.IpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Service 实例身份解析器。
 */
@Component
@RequiredArgsConstructor
public class ServiceInstanceIdentity {

    /**
     * 本次进程启动唯一标识，由节点租约和 Redis 上报共同使用。
     */
    private final String runtimeId = java.util.UUID.randomUUID().toString();

    public String getRuntimeId() {
        return runtimeId;
    }

    private static final String DEFAULT_INSTANCE_ID_PREFIX = "serialx-service-";

    private final ServiceInstanceProperties serviceInstanceProperties;

    private final Environment environment;

    private volatile String instanceId;

    private volatile String ip;

    private volatile String port;

    /**
     * 获取稳定实例 ID。
     */
    public String getInstanceId() {
        if (StringUtils.hasText(instanceId)) {
            return instanceId;
        }
        synchronized (this) {
            if (!StringUtils.hasText(instanceId)) {
                instanceId = resolveInstanceId();
            }
            return instanceId;
        }
    }

    /**
     * 获取上报 IP。
     */
    public String getIp() {
        if (StringUtils.hasText(ip)) {
            return ip;
        }
        synchronized (this) {
            if (!StringUtils.hasText(ip)) {
                ip = resolveInstanceIp();
            }
            return ip;
        }
    }

    /**
     * 获取当前服务端口。
     */
    public String getPort() {
        if (StringUtils.hasText(port) && !"0".equals(port)) {
            return port;
        }
        synchronized (this) {
            // 随机端口在注册时尚未绑定，不能让配置值 0 永久覆盖实际监听端口。
            if (!StringUtils.hasText(port) || "0".equals(port)) {
                port = resolvePort();
            }
            return port;
        }
    }

    /**
     * 获取节点上报主机标识。
     */
    public String getHost() {
        return ServiceInstanceRedisConstants.SERVICE_NAME + "/" + getIp() + ":" + getPort();
    }

    private String resolveInstanceId() {
        if (StringUtils.hasText(serviceInstanceProperties.getInstanceId())) {
            return serviceInstanceProperties.getInstanceId().trim();
        }
        String currentPort = getPort();
        // 随机端口启动前无法用于区分实例，追加本次启动 UUID；之后展示身份保持稳定。
        return DEFAULT_INSTANCE_ID_PREFIX + normalizeInstanceIdPart(getIp() + "-" + currentPort
                + ("0".equals(currentPort) ? "-" + runtimeId : ""));
    }

    private String resolveInstanceIp() {
        if (StringUtils.hasText(serviceInstanceProperties.getAdvertisedIp())) {
            return serviceInstanceProperties.getAdvertisedIp().trim();
        }
        return IpUtil.getLocalIp();
    }

    private String resolvePort() {
        String localPort = environment.getProperty("local.server.port");
        if (StringUtils.hasText(localPort)) {
            return localPort.trim();
        }
        String configuredPort = environment.getProperty("server.port");
        return StringUtils.hasText(configuredPort) ? configuredPort.trim() : "0";
    }

    private String normalizeInstanceIdPart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
