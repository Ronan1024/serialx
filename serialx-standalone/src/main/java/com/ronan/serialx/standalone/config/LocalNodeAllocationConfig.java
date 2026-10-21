package com.ronan.serialx.standalone.config;

import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.ronan.serialx.common.node.NodeAllocationGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** 单进程模式直接复用控制面事务，不向尚未开放的自身 HTTP 端口注册。 */
@Configuration
@Profile("standalone")
public class LocalNodeAllocationConfig {
    @Bean
    @Primary
    public NodeAllocationGateway localNodeAllocationGateway(SnowflakeNodeManager manager) {
        return manager;
    }
}
