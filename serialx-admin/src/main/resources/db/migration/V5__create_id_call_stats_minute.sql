CREATE TABLE sx_id_call_stats_minute
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    stat_minute      DATETIME     NOT NULL COMMENT '统计分钟，按分钟截断',
    instance_id      VARCHAR(128) NOT NULL COMMENT 'Service 实例 ID',
    host             VARCHAR(255) NOT NULL COMMENT 'Service 主机标识',
    namespace_code   VARCHAR(128) NOT NULL COMMENT 'Namespace 编码',
    client_id        VARCHAR(128) NOT NULL COMMENT '调用方客户端 ID，未知时为 unknown',
    client_name      VARCHAR(128) NOT NULL COMMENT '调用方名称，未知时为 unknown',
    operation        VARCHAR(32)  NOT NULL COMMENT '调用操作：next/batch',
    result           VARCHAR(32)  NOT NULL COMMENT '调用结果：SUCCESS/FAILED',
    error_code       VARCHAR(128) NOT NULL COMMENT '错误码，成功时为 SUCCESS',
    request_count    BIGINT       NOT NULL DEFAULT 0 COMMENT '请求次数',
    id_count         BIGINT       NOT NULL DEFAULT 0 COMMENT '生成 ID 数量',
    latency_total_ms BIGINT       NOT NULL DEFAULT 0 COMMENT '总耗时毫秒',
    latency_max_ms   BIGINT       NOT NULL DEFAULT 0 COMMENT '最大耗时毫秒',
    max_qps          BIGINT       NULL COMMENT '实例配置的最大 QPS，用于计算业务负载率',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sx_id_call_stats_minute_dim (
        stat_minute,
        instance_id,
        namespace_code,
        client_id,
        operation,
        result,
        error_code
    ),
    KEY idx_sx_id_call_stats_minute_time (stat_minute),
    KEY idx_sx_id_call_stats_minute_namespace (namespace_code),
    KEY idx_sx_id_call_stats_minute_instance (instance_id),
    KEY idx_sx_id_call_stats_minute_client (client_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='发号调用分钟级聚合统计表';
