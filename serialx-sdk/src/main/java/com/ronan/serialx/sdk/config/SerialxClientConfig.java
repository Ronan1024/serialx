package com.ronan.serialx.sdk.config;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ronan.serialx.sdk.exception.SerialxConfigException;
import org.springframework.util.StringUtils;

/**
 * SerialX 客户端配置。
 */
public class SerialxClientConfig {

    private List<String> baseUrls = new ArrayList<>();

    private String clientName;

    private String clientId;

    private String clientVersion;

    private String instanceId;

    private boolean cacheEnabled = true;

    private int defaultPrefetchSize = 100;

    private int lowWatermark = 20;

    private int maxCacheSizePerNamespace = 1000;

    private Duration connectTimeout = Duration.ofSeconds(1);

    private Duration readTimeout = Duration.ofSeconds(3);

    private int maxRetries = 2;

    private Duration healthCheckInterval = Duration.ofSeconds(10);

    private Duration unhealthyCooldown = Duration.ofSeconds(30);

    private int failureThreshold = 3;

    public void validateAndNormalize() {
        if (baseUrls == null || baseUrls.isEmpty()) {
            throw new SerialxConfigException("baseUrls 不能为空");
        }
        List<String> normalizedUrls = baseUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(this::removeTrailingSlash)
                .distinct()
                .toList();
        if (normalizedUrls.isEmpty()) {
            throw new SerialxConfigException("baseUrls 不能为空");
        }
        if (!StringUtils.hasText(clientName)) {
            throw new SerialxConfigException("clientName 不能为空");
        }
        if (!StringUtils.hasText(clientId)) {
            throw new SerialxConfigException("clientId 不能为空");
        }
        if (defaultPrefetchSize < 1 || defaultPrefetchSize > 1000) {
            throw new SerialxConfigException("defaultPrefetchSize 必须在 1 到 1000 之间");
        }
        if (lowWatermark < 0 || lowWatermark >= maxCacheSizePerNamespace) {
            throw new SerialxConfigException("lowWatermark 必须大于等于 0 且小于 maxCacheSizePerNamespace");
        }
        if (maxCacheSizePerNamespace < defaultPrefetchSize) {
            throw new SerialxConfigException("maxCacheSizePerNamespace 不能小于 defaultPrefetchSize");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new SerialxConfigException("connectTimeout 必须大于 0");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new SerialxConfigException("readTimeout 必须大于 0");
        }
        if (maxRetries < 0) {
            throw new SerialxConfigException("maxRetries 不能小于 0");
        }
        if (healthCheckInterval == null || healthCheckInterval.isNegative() || healthCheckInterval.isZero()) {
            throw new SerialxConfigException("healthCheckInterval 必须大于 0");
        }
        if (unhealthyCooldown == null || unhealthyCooldown.isNegative()) {
            throw new SerialxConfigException("unhealthyCooldown 不能小于 0");
        }
        if (failureThreshold < 1) {
            throw new SerialxConfigException("failureThreshold 必须大于 0");
        }
        baseUrls = normalizedUrls;
        clientName = clientName.trim();
        clientId = clientId.trim();
        clientVersion = StringUtils.hasText(clientVersion) ? clientVersion.trim() : null;
        instanceId = StringUtils.hasText(instanceId) ? instanceId.trim() : generateInstanceId();
    }

    private String removeTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String generateInstanceId() {
        String host = System.getenv("HOSTNAME");
        if (!StringUtils.hasText(host)) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception ex) {
                host = "unknown-host";
            }
        }
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        String pid = runtimeName.contains("@") ? runtimeName.substring(0, runtimeName.indexOf('@')) : runtimeName;
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return (host + "-" + pid + "-" + suffix).replaceAll("[^A-Za-z0-9._-]", "-");
    }

    public List<String> getBaseUrls() {
        return baseUrls;
    }

    public void setBaseUrls(List<String> baseUrls) {
        this.baseUrls = baseUrls;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getDefaultPrefetchSize() {
        return defaultPrefetchSize;
    }

    public void setDefaultPrefetchSize(int defaultPrefetchSize) {
        this.defaultPrefetchSize = defaultPrefetchSize;
    }

    public int getLowWatermark() {
        return lowWatermark;
    }

    public void setLowWatermark(int lowWatermark) {
        this.lowWatermark = lowWatermark;
    }

    public int getMaxCacheSizePerNamespace() {
        return maxCacheSizePerNamespace;
    }

    public void setMaxCacheSizePerNamespace(int maxCacheSizePerNamespace) {
        this.maxCacheSizePerNamespace = maxCacheSizePerNamespace;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public void setHealthCheckInterval(Duration healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    public Duration getUnhealthyCooldown() {
        return unhealthyCooldown;
    }

    public void setUnhealthyCooldown(Duration unhealthyCooldown) {
        this.unhealthyCooldown = unhealthyCooldown;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }
}
