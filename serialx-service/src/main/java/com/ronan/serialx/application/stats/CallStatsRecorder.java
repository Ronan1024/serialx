package com.ronan.serialx.application.stats;

import java.util.List;

import com.ronan.serialx.infra.entity.IdCallStatsMinuteDO;

/**
 * 发号调用统计本地采集接口。
 */
public interface CallStatsRecorder {

    /**
     * 记录一次发号调用。
     */
    void record(CallStatsRecord record);

    /**
     * 导出并清空当前增量统计。
     */
    List<IdCallStatsMinuteDO> drain();

    /**
     * 写库失败时将增量统计放回本地缓存，等待下次重试。
     */
    void restore(List<IdCallStatsMinuteDO> records);
}
