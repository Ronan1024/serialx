package com.ronan.serialx.sdk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import com.ronan.serialx.sdk.cache.IdCacheManager;
import com.ronan.serialx.sdk.config.SerialxClientConfig;
import com.ronan.serialx.sdk.exception.SerialxClosedException;
import com.ronan.serialx.sdk.exception.SerialxException;
import com.ronan.serialx.sdk.exception.SerialxHttpException;
import com.ronan.serialx.sdk.exception.SerialxNoAvailableEndpointException;
import com.ronan.serialx.sdk.exception.SerialxRemoteException;
import com.ronan.serialx.sdk.exception.SerialxTimeoutException;
import com.ronan.serialx.sdk.model.EndpointStatus;
import com.ronan.serialx.sdk.model.NamespaceRuntimeStatus;
import com.ronan.serialx.sdk.transport.EndpointRegistry;
import com.ronan.serialx.sdk.transport.EndpointSelector;
import com.ronan.serialx.sdk.transport.HealthChecker;
import com.ronan.serialx.sdk.transport.SerialxHttpClient;
import org.springframework.util.StringUtils;

/**
 * 默认 SerialX Java 同步客户端实现。
 */
class DefaultSerialxClient implements SerialxClient {

    private static final int MIN_BATCH_COUNT = 1;

    private static final int MAX_BATCH_COUNT = 1000;

    private final SerialxClientConfig config;

    private final SerialxHttpClient httpClient;

    private final EndpointRegistry endpointRegistry;

    private final EndpointSelector endpointSelector;

    private final HealthChecker healthChecker;

    private final IdCacheManager cacheManager;

    private final AtomicBoolean closed = new AtomicBoolean();

    DefaultSerialxClient(SerialxClientConfig config) {
        this.config = config;
        this.config.validateAndNormalize();
        // 节点注册表负责记录每个 service 地址的可用性，后续请求都基于它做选择和降级。
        this.endpointRegistry =
                new EndpointRegistry(config.getBaseUrls(), config.getFailureThreshold(), config.getUnhealthyCooldown());
        this.endpointSelector = new EndpointSelector(endpointRegistry);
        this.httpClient = new SerialxHttpClient(config);
        // 本地缓存补货也复用同一条远端批量发号链路，确保补货同样具备节点切换能力。
        BiFunction<String, Integer, List<String>> batchFetcher = this::remoteBatchNextId;
        this.cacheManager = new IdCacheManager(config, batchFetcher);
        this.healthChecker = new HealthChecker(endpointRegistry, httpClient, config);
        this.healthChecker.start();
    }

    @Override
    public String nextId(String namespaceCode) {
        ensureOpen();
        validateNamespace(namespaceCode);
        if (config.isCacheEnabled()) {
            return cacheManager.nextId(namespaceCode);
        }
        return execute(namespaceCode, (endpoint, attempt) -> httpClient.nextId(endpoint, namespaceCode, attempt));
    }

    @Override
    public List<String> batchNextId(String namespaceCode, int count) {
        ensureOpen();
        validateNamespace(namespaceCode);
        validateCount(count);
        if (config.isCacheEnabled()) {
            return cacheManager.batchNextId(namespaceCode, count);
        }
        return remoteBatchNextId(namespaceCode, count);
    }

    @Override
    public NamespaceRuntimeStatus getRuntimeStatus(String namespaceCode) {
        ensureOpen();
        validateNamespace(namespaceCode);
        return execute(namespaceCode, (endpoint, attempt) -> httpClient.getRuntimeStatus(endpoint, namespaceCode, attempt));
    }

    @Override
    public List<EndpointStatus> getEndpointStatuses() {
        return endpointRegistry.snapshots();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            healthChecker.close();
            cacheManager.close();
        }
    }

    private List<String> remoteBatchNextId(String namespaceCode, int count) {
        validateCount(count);
        return execute(namespaceCode, (endpoint, attempt) -> httpClient.batchNextId(endpoint, namespaceCode, count, attempt));
    }

    private <T> T execute(String namespaceCode, AttemptOperation<T> operation) {
        int maxAttempts = Math.max(1, Math.min(1 + config.getMaxRetries(), config.getBaseUrls().size()));
        Set<String> tried = new HashSet<>();
        SerialxException lastException = null;
        String lastEndpoint = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // 多节点场景下，同一次业务调用尽量不重复打到已失败节点，避免放大单点故障。
            String endpoint = endpointSelector.select(config.getBaseUrls().size() == 1 ? Set.of() : tried);
            if (endpoint == null) {
                break;
            }
            tried.add(endpoint);
            lastEndpoint = endpoint;
            long start = System.nanoTime();
            try {
                T result = operation.apply(endpoint, attempt);
                endpointRegistry.markSuccess(endpoint, elapsedMillis(start));
                return result;
            } catch (SerialxRemoteException ex) {
                // 业务失败说明服务端已明确处理请求，不能通过切节点重试来掩盖调用方或 namespace 问题。
                throw ex;
            } catch (SerialxHttpException ex) {
                lastException = ex;
                if (!shouldRetry(ex)) {
                    throw ex;
                }
                // 网络异常、超时和 5xx 会触发节点降级；若服务端已发号但响应丢失，重试只会跳号，不会重复。
                endpointRegistry.markFailure(endpoint);
            } catch (SerialxTimeoutException ex) {
                lastException = ex;
                endpointRegistry.markFailure(endpoint);
            }
        }
        throw new SerialxNoAvailableEndpointException(
                "没有可用的 SerialX service 节点",
                lastException,
                namespaceCode,
                tried.size(),
                lastEndpoint,
                config.getClientName(),
                config.getClientId(),
                config.getInstanceId());
    }

    private boolean shouldRetry(SerialxHttpException ex) {
        Integer status = ex.getResponseCode();
        return status == null || status >= 500;
    }

    private long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new SerialxClosedException("SerialX 客户端已关闭");
        }
    }

    private void validateNamespace(String namespaceCode) {
        if (!StringUtils.hasText(namespaceCode)) {
            throw new IllegalArgumentException("namespaceCode 不能为空");
        }
    }

    private void validateCount(int count) {
        if (count < MIN_BATCH_COUNT || count > MAX_BATCH_COUNT) {
            throw new IllegalArgumentException("count 必须在 1 到 1000 之间");
        }
    }

    private interface AttemptOperation<T> {

        T apply(String endpoint, int attempt);
    }
}
