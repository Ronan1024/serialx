package com.ronan.serialx.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 发号调用分钟级聚合统计持久化对象。
 */
@Getter
@Setter
@TableName("sx_id_call_stats_minute")
public class IdCallStatsMinuteDO extends BaseDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计分钟，按分钟截断。
     */
    @TableField("stat_minute")
    private LocalDateTime statMinute;

    /**
     * Service 实例 ID。
     */
    @TableField("instance_id")
    private String instanceId;

    /**
     * Service 主机标识。
     */
    private String host;

    /**
     * Namespace 编码。
     */
    @TableField("namespace_code")
    private String namespaceCode;

    /**
     * 调用方客户端 ID。
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 调用方名称。
     */
    @TableField("client_name")
    private String clientName;

    /**
     * 调用操作。
     */
    private String operation;

    /**
     * 调用结果。
     */
    private String result;

    /**
     * 错误码。
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 请求次数。
     */
    @TableField("request_count")
    private Long requestCount;

    /**
     * 生成 ID 数量。
     */
    @TableField("id_count")
    private Long idCount;

    /**
     * 总耗时毫秒。
     */
    @TableField("latency_total_ms")
    private Long latencyTotalMs;

    /**
     * 最大耗时毫秒。
     */
    @TableField("latency_max_ms")
    private Long latencyMaxMs;

    /**
     * 实例配置的最大 QPS。
     */
    @TableField("max_qps")
    private Long maxQps;
}
