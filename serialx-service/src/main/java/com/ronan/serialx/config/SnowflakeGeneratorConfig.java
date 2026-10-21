package com.ronan.serialx.config;

import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.application.node.SnowflakeNodeLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 所有 Namespace 共享一个实例生成器，位布局固定以避免配置产生碰撞。
 */
@Configuration
public class SnowflakeGeneratorConfig {
    @Bean
    public SnowflakeGenerator snowflakeGenerator(SnowflakeNodeLifecycle lifecycle) {
        return lifecycle.generator();
    }
}
