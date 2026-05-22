package com.ronan.serialx.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ronan.serialx.infra.entity.IdCallStatsMinuteDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 发号调用分钟级聚合统计 Mapper。
 */
@Mapper
public interface IdCallStatsMinuteMapper extends BaseMapper<IdCallStatsMinuteDO> {

    /**
     * 批量累加写入分钟级统计。
     */
    @Insert("""
            <script>
            INSERT INTO sx_id_call_stats_minute (
                stat_minute,
                instance_id,
                host,
                namespace_code,
                client_id,
                client_name,
                operation,
                result,
                error_code,
                request_count,
                id_count,
                latency_total_ms,
                latency_max_ms,
                max_qps
            )
            VALUES
            <foreach collection="records" item="item" separator=",">
                (
                    #{item.statMinute},
                    #{item.instanceId},
                    #{item.host},
                    #{item.namespaceCode},
                    #{item.clientId},
                    #{item.clientName},
                    #{item.operation},
                    #{item.result},
                    #{item.errorCode},
                    #{item.requestCount},
                    #{item.idCount},
                    #{item.latencyTotalMs},
                    #{item.latencyMaxMs},
                    #{item.maxQps}
                )
            </foreach>
            ON DUPLICATE KEY UPDATE
                host = VALUES(host),
                client_name = VALUES(client_name),
                request_count = request_count + VALUES(request_count),
                id_count = id_count + VALUES(id_count),
                latency_total_ms = latency_total_ms + VALUES(latency_total_ms),
                latency_max_ms = GREATEST(latency_max_ms, VALUES(latency_max_ms)),
                max_qps = VALUES(max_qps),
                updated_at = CURRENT_TIMESTAMP
            </script>
            """)
    int batchUpsert(@Param("records") List<IdCallStatsMinuteDO> records);
}
