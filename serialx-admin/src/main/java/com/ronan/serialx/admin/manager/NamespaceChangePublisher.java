package com.ronan.serialx.admin.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.model.NamespaceChangeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Namespace 变更通知发布器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NamespaceChangePublisher {

    /**
     * Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * JSON 序列化组件。
     */
    private final ObjectMapper objectMapper;

    /**
     * 发布 Namespace 变更通知。
     */
    public void publish(NamespaceChangeMessage message) {
        try {
            stringRedisTemplate.convertAndSend(
                    NamespaceChangeMessage.DEFAULT_CHANNEL, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ex) {
            log.warn("namespace change message serialization failed, namespaceCode={}", message.getNamespaceCode(), ex);
        } catch (Exception ex) {
            // Redis 通知只负责加速感知，失败后由 service 轮询兜底。
            log.warn("namespace change message publish failed, namespaceCode={}", message.getNamespaceCode(), ex);
        }
    }
}
