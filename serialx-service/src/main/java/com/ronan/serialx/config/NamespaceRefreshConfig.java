package com.ronan.serialx.config;

import com.ronan.serialx.service.namespace.NamespaceChangeSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Namespace 刷新相关配置。
 */
@Configuration
@RequiredArgsConstructor
public class NamespaceRefreshConfig {

    /**
     * Namespace 刷新配置属性。
     */
    private final ServiceInstanceProperties serviceInstanceProperties;

    /**
     * 注册 Redis 监听容器。
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory redisConnectionFactory, NamespaceChangeSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(subscriber, new PatternTopic(serviceInstanceProperties.getChannel()));
        return container;
    }
}
