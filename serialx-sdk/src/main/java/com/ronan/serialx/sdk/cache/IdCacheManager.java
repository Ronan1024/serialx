package com.ronan.serialx.sdk.cache;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import com.ronan.serialx.sdk.config.SerialxClientConfig;
import com.ronan.serialx.sdk.exception.SerialxClosedException;

/**
 * 多 Namespace 本地缓存管理器。
 */
public class IdCacheManager implements AutoCloseable {

    private final SerialxClientConfig config;

    private final BiFunction<String, Integer, List<String>> remoteBatchFetcher;

    private final ConcurrentMap<String, NamespaceIdPool> pools = new ConcurrentHashMap<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final ExecutorService refillExecutor =
            Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, "serialx-id-cache-refill");
                thread.setDaemon(true);
                return thread;
            });

    public IdCacheManager(SerialxClientConfig config, BiFunction<String, Integer, List<String>> remoteBatchFetcher) {
        this.config = config;
        this.remoteBatchFetcher = remoteBatchFetcher;
    }

    public String nextId(String namespaceCode) {
        ensureOpen();
        return pool(namespaceCode).nextId();
    }

    public List<String> batchNextId(String namespaceCode, int count) {
        ensureOpen();
        return pool(namespaceCode).batchNextId(count);
    }

    private NamespaceIdPool pool(String namespaceCode) {
        return pools.computeIfAbsent(namespaceCode,
                key -> new NamespaceIdPool(key, config, remoteBatchFetcher, refillExecutor));
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new SerialxClosedException("SerialX 客户端已关闭");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            refillExecutor.shutdownNow();
        }
    }
}
