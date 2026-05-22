package com.ronan.serialx.sdk.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import com.ronan.serialx.sdk.config.SerialxClientConfig;

/**
 * 单个 Namespace 的本地 ID 缓存池。
 */
public class NamespaceIdPool {

    private final String namespaceCode;

    private final SerialxClientConfig config;

    private final BiFunction<String, Integer, List<String>> remoteBatchFetcher;

    private final ExecutorService refillExecutor;

    private final Queue<String> queue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean refilling = new AtomicBoolean();

    public NamespaceIdPool(
            String namespaceCode,
            SerialxClientConfig config,
            BiFunction<String, Integer, List<String>> remoteBatchFetcher,
            ExecutorService refillExecutor) {
        this.namespaceCode = namespaceCode;
        this.config = config;
        this.remoteBatchFetcher = remoteBatchFetcher;
        this.refillExecutor = refillExecutor;
    }

    public String nextId() {
        String id = queue.poll();
        if (id == null) {
            // 池空时同步补货，调用方阻塞等待一批新 ID，避免返回空值或降级到不确定状态。
            refillSync(config.getDefaultPrefetchSize());
            id = queue.poll();
        }
        triggerAsyncRefillIfNeeded();
        return id;
    }

    public List<String> batchNextId(int count) {
        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            String id = queue.poll();
            if (id == null) {
                int missing = count - result.size();
                int fetchCount = Math.max(missing, config.getDefaultPrefetchSize());
                // 批量请求先消费本地缓存，不足部分一次性远端补足，减少多次 HTTP 往返。
                refillSync(Math.min(fetchCount, 1000));
                id = queue.poll();
                if (id == null) {
                    break;
                }
            }
            result.add(id);
        }
        triggerAsyncRefillIfNeeded();
        return result;
    }

    int size() {
        return queue.size();
    }

    private void triggerAsyncRefillIfNeeded() {
        if (queue.size() > config.getLowWatermark()) {
            return;
        }
        if (refilling.compareAndSet(false, true)) {
            // 低水位补货只允许一个后台任务执行，避免并发补货超过单 namespace 最大缓存容量。
            refillExecutor.execute(() -> {
                try {
                    refill(config.getDefaultPrefetchSize());
                } finally {
                    refilling.set(false);
                }
            });
        }
    }

    private void refillSync(int requestedCount) {
        if (refilling.compareAndSet(false, true)) {
            try {
                refill(requestedCount);
            } finally {
                refilling.set(false);
            }
        } else {
            // 已有补货任务在进行时，当前线程短暂等待结果，避免同一 namespace 重复远端拉取。
            while (refilling.get()) {
                Thread.onSpinWait();
            }
        }
    }

    private void refill(int requestedCount) {
        int capacity = config.getMaxCacheSizePerNamespace() - queue.size();
        int fetchCount = Math.min(Math.min(requestedCount, 1000), capacity);
        if (fetchCount <= 0) {
            return;
        }
        // 预取后的 ID 只存在本地内存；进程退出未消费会形成跳号，但不会破坏全局唯一性。
        queue.addAll(remoteBatchFetcher.apply(namespaceCode, fetchCount));
    }
}
