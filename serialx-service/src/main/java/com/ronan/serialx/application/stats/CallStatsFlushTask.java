package com.ronan.serialx.application.stats;

import java.util.List;

import com.ronan.serialx.config.CallStatsProperties;
import com.ronan.serialx.infra.entity.IdCallStatsMinuteDO;
import com.ronan.serialx.infra.mapper.IdCallStatsMinuteMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 调用统计异步落库任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallStatsFlushTask {

    private final CallStatsRecorder callStatsRecorder;

    private final IdCallStatsMinuteMapper idCallStatsMinuteMapper;

    private final CallStatsProperties callStatsProperties;

    /**
     * 定时批量写入增量统计。
     */
    @Scheduled(fixedDelayString = "#{@callStatsProperties.flushInterval.toMillis()}")
    public void flush() {
        if (!callStatsProperties.isEnabled()) {
            return;
        }
        doFlush();
    }

    @PreDestroy
    public void flushOnShutdown() {
        doFlush();
    }

    private void doFlush() {
        List<IdCallStatsMinuteDO> records = callStatsRecorder.drain();
        if (records.isEmpty()) {
            return;
        }
        try {
            idCallStatsMinuteMapper.batchUpsert(records);
        } catch (RuntimeException ex) {
            callStatsRecorder.restore(records);
            log.warn("flush id call stats failed, records={}", records.size(), ex);
        }
    }
}
