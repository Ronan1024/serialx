CREATE TABLE admin_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username      VARCHAR(64)  NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt加密后的密码哈希',
    display_name  VARCHAR(64)  NOT NULL COMMENT '用户展示名称',
    email         VARCHAR(128) NULL COMMENT '邮箱地址',
    mobile        VARCHAR(32)  NULL COMMENT '手机号码',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '用户状态：0-禁用，1-启用',
    roles         VARCHAR(128) NOT NULL DEFAULT 'ADMIN' COMMENT '用户角色，多个角色使用英文逗号分隔',
    last_login_at DATETIME     NULL COMMENT '最后登录时间',
    created_at    DATETIME     NOT NULL COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username),
    KEY idx_admin_user_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='后台管理用户表';
