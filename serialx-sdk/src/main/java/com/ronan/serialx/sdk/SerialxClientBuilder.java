package com.ronan.serialx.sdk;

import java.time.Duration;
import java.util.List;

import com.ronan.serialx.sdk.config.SerialxClientConfig;

/**
 * SerialX 客户端构建器。
 */
public class SerialxClientBuilder {

    private final SerialxClientConfig config = new SerialxClientConfig();

    public SerialxClientBuilder baseUrls(List<String> baseUrls) {
        config.setBaseUrls(baseUrls);
        return this;
    }

    public SerialxClientBuilder clientName(String clientName) {
        config.setClientName(clientName);
        return this;
    }

    public SerialxClientBuilder clientId(String clientId) {
        config.setClientId(clientId);
        return this;
    }

    public SerialxClientBuilder clientVersion(String clientVersion) {
        config.setClientVersion(clientVersion);
        return this;
    }

    public SerialxClientBuilder instanceId(String instanceId) {
        config.setInstanceId(instanceId);
        return this;
    }

    public SerialxClientBuilder cacheEnabled(boolean cacheEnabled) {
        config.setCacheEnabled(cacheEnabled);
        return this;
    }

    public SerialxClientBuilder defaultPrefetchSize(int defaultPrefetchSize) {
        config.setDefaultPrefetchSize(defaultPrefetchSize);
        return this;
    }

    public SerialxClientBuilder lowWatermark(int lowWatermark) {
        config.setLowWatermark(lowWatermark);
        return this;
    }

    public SerialxClientBuilder maxCacheSizePerNamespace(int maxCacheSizePerNamespace) {
        config.setMaxCacheSizePerNamespace(maxCacheSizePerNamespace);
        return this;
    }

    public SerialxClientBuilder connectTimeout(Duration connectTimeout) {
        config.setConnectTimeout(connectTimeout);
        return this;
    }

    public SerialxClientBuilder readTimeout(Duration readTimeout) {
        config.setReadTimeout(readTimeout);
        return this;
    }

    public SerialxClientBuilder maxRetries(int maxRetries) {
        config.setMaxRetries(maxRetries);
        return this;
    }

    public SerialxClientBuilder healthCheckInterval(Duration healthCheckInterval) {
        config.setHealthCheckInterval(healthCheckInterval);
        return this;
    }

    public SerialxClientBuilder unhealthyCooldown(Duration unhealthyCooldown) {
        config.setUnhealthyCooldown(unhealthyCooldown);
        return this;
    }

    public SerialxClientBuilder failureThreshold(int failureThreshold) {
        config.setFailureThreshold(failureThreshold);
        return this;
    }

    public SerialxClient build() {
        return SerialxClient.create(config);
    }
}
