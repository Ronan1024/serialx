package com.ronan.serialx.application.stats;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.ronan.serialx.config.CallStatsProperties;
import com.ronan.serialx.infra.entity.IdCallStatsMinuteDO;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于内存的发号调用增量统计器。
 */
@Component
@RequiredArgsConstructor
public class InMemoryCallStatsRecorder implements CallStatsRecorder {

    private static final String UNKNOWN = "unknown";

    private static final String SUCCESS = "SUCCESS";

    private static final String FAILED = "FAILED";

    private final ServiceInstanceIdentity serviceInstanceIdentity;

    private final CallStatsProperties callStatsProperties;

    private final ConcurrentHashMap<StatsKey, StatsCounter> counters = new ConcurrentHashMap<>();

    @Override
    public void record(CallStatsRecord record) {
        if (!callStatsProperties.isEnabled() || record == null || !StringUtils.hasText(record.getNamespaceCode())) {
            return;
        }
        StatsKey key = new StatsKey(
                LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
                serviceInstanceIdentity.getInstanceId(),
                serviceInstanceIdentity.getHost(),
                normalize(record.getNamespaceCode()),
                normalize(record.getClientId()),
                normalize(record.getClientName()),
                normalize(record.getOperation()),
                record.isSuccess() ? SUCCESS : FAILED,
                record.isSuccess() ? SUCCESS : normalize(record.getErrorCode()),
                callStatsProperties.getMaxQps());
        counters.computeIfAbsent(key, ignored -> new StatsCounter())
                .add(record.getGeneratedCount(), Math.max(0L, record.getLatencyMs()));
    }

    @Override
    public List<IdCallStatsMinuteDO> drain() {
        List<IdCallStatsMinuteDO> records = new ArrayList<>();
        counters.forEach((key, counter) -> {
            CounterSnapshot snapshot = counter.drain();
            if (snapshot.requests() <= 0) {
                return;
            }
            records.add(toDO(key, snapshot));
        });
        counters.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return records;
    }

    @Override
    public void restore(List<IdCallStatsMinuteDO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(record -> counters
                .computeIfAbsent(toKey(record), ignored -> new StatsCounter())
                .addSnapshot(new CounterSnapshot(
                        value(record.getRequestCount()),
                        value(record.getIdCount()),
                        value(record.getLatencyTotalMs()),
                        value(record.getLatencyMaxMs()))));
    }

    private IdCallStatsMinuteDO toDO(StatsKey key, CounterSnapshot snapshot) {
        IdCallStatsMinuteDO item = new IdCallStatsMinuteDO();
        item.setStatMinute(key.statMinute());
        item.setInstanceId(key.instanceId());
        item.setHost(key.host());
        item.setNamespaceCode(key.namespaceCode());
        item.setClientId(key.clientId());
        item.setClientName(key.clientName());
        item.setOperation(key.operation());
        item.setResult(key.result());
        item.setErrorCode(key.errorCode());
        item.setRequestCount(snapshot.requests());
        item.setIdCount(snapshot.ids());
        item.setLatencyTotalMs(snapshot.latencyMs());
        item.setLatencyMaxMs(snapshot.maxLatencyMs());
        item.setMaxQps(key.maxQps() == null || key.maxQps() <= 0 ? null : key.maxQps());
        return item;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : UNKNOWN;
    }

    private StatsKey toKey(IdCallStatsMinuteDO record) {
        return new StatsKey(
                record.getStatMinute(),
                normalize(record.getInstanceId()),
                normalize(record.getHost()),
                normalize(record.getNamespaceCode()),
                normalize(record.getClientId()),
                normalize(record.getClientName()),
                normalize(record.getOperation()),
                normalize(record.getResult()),
                normalize(record.getErrorCode()),
                record.getMaxQps());
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private record StatsKey(
            LocalDateTime statMinute,
            String instanceId,
            String host,
            String namespaceCode,
            String clientId,
            String clientName,
            String operation,
            String result,
            String errorCode,
            Long maxQps) {
    }

    private record CounterSnapshot(long requests, long ids, long latencyMs, long maxLatencyMs) {
    }

    private static class StatsCounter {

        private final LongAdder requests = new LongAdder();

        private final LongAdder ids = new LongAdder();

        private final LongAdder latencyMs = new LongAdder();

        private final AtomicLong maxLatencyMs = new AtomicLong();

        void add(int generatedCount, long elapsedMs) {
            requests.increment();
            ids.add(Math.max(0, generatedCount));
            latencyMs.add(elapsedMs);
            maxLatencyMs.accumulateAndGet(elapsedMs, Math::max);
        }

        void addSnapshot(CounterSnapshot snapshot) {
            requests.add(snapshot.requests());
            ids.add(snapshot.ids());
            latencyMs.add(snapshot.latencyMs());
            maxLatencyMs.accumulateAndGet(snapshot.maxLatencyMs(), Math::max);
        }

        CounterSnapshot drain() {
            return new CounterSnapshot(
                    requests.sumThenReset(),
                    ids.sumThenReset(),
                    latencyMs.sumThenReset(),
                    maxLatencyMs.getAndSet(0));
        }

        boolean isEmpty() {
            return requests.sum() == 0 && ids.sum() == 0 && latencyMs.sum() == 0 && maxLatencyMs.get() == 0;
        }
    }
}
