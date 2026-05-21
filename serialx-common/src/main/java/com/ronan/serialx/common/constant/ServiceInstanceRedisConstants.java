package com.ronan.serialx.common.constant;

/**
 * Service 实例运行态 Redis 结构常量。
 */
public final class ServiceInstanceRedisConstants {

    public static final String SERVICE_NAME = "serialx-service";

    public static final String ACTIVE = "ACTIVE";

    public static final String OFFLINE = "OFFLINE";

    public static final String LOAD_FAILED = "LOAD_FAILED";

    public static final String DISABLED = "DISABLED";

    public static final String NODE_NAMESPACE = "__node__";

    public static final String INSTANCE_KEY_PATTERN = "serialx:service:instance:*";

    public static final String INSTANCE_NAMESPACE_KEY_PATTERN = "serialx:service:instance-namespace:*";

    public static final String INSTANCE_HEARTBEAT_KEY_PATTERN = "serialx:service:instance-heartbeat:*";

    public static final String LEGACY_INSTANCE_STATUS_KEY_PATTERN = "serialx:service:instance-status:*";

    public static final String INSTANCE_KEY_FORMAT = "serialx:service:instance:%s";

    public static final String INSTANCE_NAMESPACE_KEY_FORMAT = "serialx:service:instance-namespace:%s:%s";

    public static final String INSTANCE_HEARTBEAT_KEY_FORMAT = "serialx:service:instance-heartbeat:%s";

    private ServiceInstanceRedisConstants() {
    }

    /**
     * Redis Hash 字段名。
     */
    public static final class Fields {

        public static final String INSTANCE_ID = "instanceId";

        public static final String RUNTIME_ID = "runtimeId";

        public static final String HOST_NAME = "hostName";

        public static final String HOST = "host";

        public static final String IP = "ip";

        public static final String PORT = "port";

        public static final String STATUS = "status";

        public static final String NAMESPACE_CODE = "namespaceCode";

        public static final String LOADED_VERSION = "loadedVersion";

        public static final String RUNTIME_STATUS = "runtimeStatus";

        public static final String CREATE_TIME = "createTime";

        public static final String LAST_REPORT_TIME = "lastReportTime";

        public static final String LAST_OFFLINE_TIME = "lastOfflineTime";

        private Fields() {
        }
    }
}
