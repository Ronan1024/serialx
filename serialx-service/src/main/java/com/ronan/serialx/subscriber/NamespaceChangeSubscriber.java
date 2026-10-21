package com.ronan.serialx.subscriber;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.application.refresh.NamespaceRuntimeLoader;
import com.ronan.serialx.common.model.NamespaceChangeMessage;
import com.ronan.serialx.config.ServiceInstanceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Namespace 变更订阅器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NamespaceChangeSubscriber implements MessageListener {

    private static final Set<String> REFRESH_EVENTS = Set.of("PUBLISH", "ENABLE", "ROLLBACK", "DISABLE", "DELETE");

    private final ObjectMapper objectMapper;

    private final NamespaceRuntimeLoader namespaceRuntimeLoader;

    private final ServiceInstanceProperties serviceInstanceProperties;

    /**
     * 处理同进程 admin 发布的本地 Namespace 变更事件。
     */
    @EventListener
    public void onLocalNamespaceChange(NamespaceChangeMessage message) {
        if (!serviceInstanceProperties.isNamespaceRefreshEnabled()) {
            log.debug("Namespace 变更接收已禁用，source=LOCAL");
            return;
        }
        log.debug("收到本地 Namespace 变更");
        handleChangeMessage(message);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (!serviceInstanceProperties.isNamespaceRefreshEnabled()) {
            log.debug("Namespace 变更接收已禁用，source=REDIS");
            return;
        }
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.debug("收到 Redis Namespace 变更，payloadBytes={}", message.getBody().length);
        try {
            NamespaceChangeMessage changeMessage = objectMapper.readValue(body, NamespaceChangeMessage.class);
            handleChangeMessage(changeMessage);
        } catch (Exception ex) {
            // 消息正文和解析异常均可能含配置或凭证，不写入日志。
            log.warn("Namespace 变更处理失败，payloadBytes={}, exceptionType={}", message.getBody().length, ex.getClass().getSimpleName());
        }
    }

    private void handleChangeMessage(NamespaceChangeMessage message) {
        if (message == null || !StringUtils.hasText(message.getNamespaceCode())) {
            log.debug("忽略缺少 Namespace 标识的变更");
            return;
        }
        String eventType = normalizeEventType(message.getEventType());
        if (REFRESH_EVENTS.contains(eventType)) {
            log.debug("Namespace 变更处理开始，namespaceCode={}, version={}, eventType={}",
                    message.getNamespaceCode(), message.getVersion(), eventType);
            namespaceRuntimeLoader.refresh(message.getNamespaceCode(), message.getVersion());
            log.debug("Namespace 变更处理完成，namespaceCode={}, version={}, eventType={}",
                    message.getNamespaceCode(), message.getVersion(), eventType);
            return;
        }
        log.warn("不支持的 Namespace 变更事件，namespaceCode={}, version={}", message.getNamespaceCode(), message.getVersion());
    }

    private String normalizeEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return "";
        }
        return eventType.trim().toUpperCase(Locale.ROOT);
    }
}
