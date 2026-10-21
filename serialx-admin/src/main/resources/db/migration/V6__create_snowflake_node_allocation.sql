CREATE TABLE sx_snowflake_node (
    datacenter_id INT NOT NULL,
    worker_id INT NOT NULL,
    owner_runtime_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_expire_at_ms BIGINT NOT NULL DEFAULT 0,
    reserved_until_ms BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (datacenter_id, worker_id),
    CHECK (datacenter_id BETWEEN 0 AND 31),
    CHECK (worker_id BETWEEN 0 AND 31),
    CHECK (reserved_until_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='雪花节点槽位，历史时间水位不得回退';

CREATE TABLE sx_snowflake_node_session (
    runtime_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    instance_id VARCHAR(128) NOT NULL,
    datacenter_id INT NOT NULL,
    worker_id INT NOT NULL,
    valid_from_ms BIGINT NOT NULL,
    reserved_until_ms BIGINT NOT NULL,
    lease_expire_at_ms BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (runtime_id),
    KEY idx_sx_node_session_node (datacenter_id, worker_id),
    CHECK (datacenter_id BETWEEN 0 AND 31),
    CHECK (worker_id BETWEEN 0 AND 31),
    CHECK (valid_from_ms <= reserved_until_ms),
    CHECK (status IN ('ACTIVE', 'EXPIRED', 'RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='雪花启动会话，不删除旧身份，不复活失效会话';

INSERT INTO sx_snowflake_node (datacenter_id, worker_id)
SELECT dc.n, worker.n
FROM (
    SELECT ones.n + tens.n * 8 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
          UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) ones
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) tens
) dc
CROSS JOIN (
    SELECT ones.n + tens.n * 8 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
          UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) ones
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) tens
) worker;
