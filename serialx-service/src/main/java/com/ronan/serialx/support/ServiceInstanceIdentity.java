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
        if (StringUtils.hasText(port)) {
            return port;
        }
        synchronized (this) {
            if (!StringUtils.hasText(port)) {
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
        return DEFAULT_INSTANCE_ID_PREFIX + normalizeInstanceIdPart(getIp() + "-" + getPort());
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
