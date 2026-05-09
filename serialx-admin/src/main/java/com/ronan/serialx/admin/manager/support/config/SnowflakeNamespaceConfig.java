package com.ronan.serialx.admin.manager.support.config;

/**
 * Snowflake 模式配置。
 */
public class SnowflakeNamespaceConfig implements NamespaceModeConfig {

    @Override
    public void validate() {
        // Snowflake 的节点身份配置属于 service 实例级参数，不在 namespace 维度校验。
    }
}
