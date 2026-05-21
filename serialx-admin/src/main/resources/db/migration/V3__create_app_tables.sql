CREATE TABLE sx_app
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_key      VARCHAR(64)  NOT NULL COMMENT '业务调用方 AppKey',
    app_name     VARCHAR(128) NOT NULL COMMENT '业务调用方应用名称',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '应用状态：0-禁用，1-启用',
    owner        VARCHAR(64)  NOT NULL COMMENT '负责人',
    remark       VARCHAR(512) NULL COMMENT '备注说明',
    created_at   DATETIME     NOT NULL COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sx_app_key (app_key),
    KEY idx_sx_app_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='业务调用方应用表';

CREATE TABLE sx_app_secret
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id      BIGINT       NOT NULL COMMENT '应用主键ID',
    secret_hash VARCHAR(128) NOT NULL COMMENT 'AppSecret 的 SHA-256 摘要',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '密钥状态：0-禁用，1-启用',
    created_at  DATETIME     NOT NULL COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sx_app_secret_app_id (app_id),
    KEY idx_sx_app_secret_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='业务调用方应用密钥表';
