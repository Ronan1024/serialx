package com.ronan.serialx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Snowflake 节点身份配置属性。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "serialx.service.snowflake")
public class SnowflakeNodeProperties {

    /**
     * 当前实例 worker ID。
     */
    private long workerId;

    /**
     * 当前实例 datacenter ID。
     */
    private long datacenterId;

    /**
     * Worker ID 占用位数。
     */
    private long workerIdBits = 5;

    /**
     * Datacenter ID 占用位数。
     */
    private long datacenterIdBits = 5;

    /**
     * 序列号占用位数。
     */
    private long sequenceBits = 12;
}
