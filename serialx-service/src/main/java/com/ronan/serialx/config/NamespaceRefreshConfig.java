package com.ronan.serialx.config;

import com.ronan.serialx.subscriber.NamespaceChangeSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Namespace 刷新 Redis 订阅配置。
 */
@Configuration
@RequiredArgsConstructor
public class NamespaceRefreshConfig {

    private final ServiceInstanceProperties serviceInstanceProperties;

    private final NamespaceChangeSubscriber namespaceChangeSubscriber;

    /**
     * Redis 消息监听容器。
     */
    @Bean
    public RedisMessageListenerContainer namespaceRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                namespaceChangeSubscriber,
                new ChannelTopic(serviceInstanceProperties.getChannel()));
        return container;
    }
}
