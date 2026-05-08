CREATE TABLE sx_namespace
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    namespace_code  VARCHAR(64)  NOT NULL COMMENT 'Namespace 编码',
    namespace_name  VARCHAR(128) NOT NULL COMMENT 'Namespace 名称',
    id_mode         TINYINT      NOT NULL COMMENT '发号模式：1-SEGMENT，2-SNOWFLAKE',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT 'Namespace 状态：0-DRAFT，1-ENABLED，2-DISABLED',
    owner           VARCHAR(64)  NOT NULL COMMENT '负责人',
    remark          VARCHAR(512) NULL COMMENT '备注说明',
    current_version INT          NOT NULL DEFAULT 0 COMMENT '当前生效版本号，0 表示尚未发布',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    created_at      DATETIME     NOT NULL COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sx_namespace_code (namespace_code),
    KEY idx_sx_namespace_status (status),
    KEY idx_sx_namespace_mode (id_mode)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Namespace 主表';

CREATE TABLE sx_namespace_config
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    namespace_id    BIGINT       NOT NULL COMMENT 'Namespace 主键ID',
    version         INT          NOT NULL COMMENT '配置版本号',
    id_mode         TINYINT      NOT NULL COMMENT '发号模式：1-SEGMENT，2-SNOWFLAKE',
    config_json     JSON         NOT NULL COMMENT '版本配置内容 JSON',
    config_checksum VARCHAR(64)  NOT NULL COMMENT '配置摘要',
    publish_status  TINYINT      NOT NULL COMMENT '发布状态：0-EDITING，1-PUBLISHED，2-ROLLED_BACK',
    published_by    VARCHAR(64)  NULL COMMENT '发布人',
    published_at    DATETIME     NULL COMMENT '发布时间',
    created_at      DATETIME     NOT NULL COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sx_namespace_config_version (namespace_id, version),
    KEY idx_sx_namespace_config_publish_status (publish_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Namespace 版本配置表';

CREATE TABLE sx_namespace_change_log
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    namespace_id BIGINT      NOT NULL COMMENT 'Namespace 主键ID',
    version      INT         NOT NULL DEFAULT 0 COMMENT '关联版本号',
    action_type  TINYINT     NOT NULL COMMENT '动作类型：1-CREATE，2-UPDATE，3-SAVE_DRAFT，4-PUBLISH，5-ROLLBACK，6-ENABLE，7-DISABLE，8-DELETE',
    before_json  JSON        NULL COMMENT '变更前快照 JSON',
    after_json   JSON        NULL COMMENT '变更后快照 JSON',
    operator     VARCHAR(64) NOT NULL COMMENT '操作人',
    created_at   DATETIME    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sx_namespace_change_log_namespace (namespace_id),
    KEY idx_sx_namespace_change_log_action_type (action_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Namespace 变更日志表';

CREATE TABLE sx_service_instance_config_status
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    instance_id      VARCHAR(128) NOT NULL COMMENT '实例ID',
    service_name     VARCHAR(64)  NOT NULL COMMENT '服务名称',
    host             VARCHAR(128) NOT NULL COMMENT '主机标识',
    namespace_code   VARCHAR(64)  NOT NULL COMMENT 'Namespace 编码',
    loaded_version   INT          NOT NULL COMMENT '已加载版本号',
    runtime_status   VARCHAR(64)  NOT NULL COMMENT '运行时状态',
    last_report_time DATETIME     NOT NULL COMMENT '最后上报时间',
    created_at       DATETIME     NOT NULL COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sx_service_instance_namespace (instance_id, namespace_code),
    KEY idx_sx_service_instance_last_report_time (last_report_time),
    KEY idx_sx_service_instance_namespace_code (namespace_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Service 实例配置状态表';
