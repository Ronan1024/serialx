package com.ronan.serialx.subscriber;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.application.refresh.NamespaceRuntimeLoader;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
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

    private static final Set<String> REFRESH_EVENTS = Set.of("PUBLISH", "ENABLE", "ROLLBACK");

    private static final String DISABLE_EVENT = "DISABLE";

    private static final String DELETE_EVENT = "DELETE";

    private final ObjectMapper objectMapper;

    private final NamespaceRuntimeLoader namespaceRuntimeLoader;

    private final ServiceInstanceProperties serviceInstanceProperties;

    /**
     * 处理同进程 admin 发布的本地 Namespace 变更事件。
     */
    @EventListener
    public void onLocalNamespaceChange(NamespaceChangeMessage message) {
        if (!serviceInstanceProperties.isNamespaceRefreshEnabled()) {
            return;
        }
        handleChangeMessage(message);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (!serviceInstanceProperties.isNamespaceRefreshEnabled()) {
            return;
        }
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            NamespaceChangeMessage changeMessage = objectMapper.readValue(body, NamespaceChangeMessage.class);
            handleChangeMessage(changeMessage);
        } catch (Exception ex) {
            log.warn("handle namespace change message failed, message={}", body, ex);
        }
    }

    private void handleChangeMessage(NamespaceChangeMessage message) {
        if (message == null || !StringUtils.hasText(message.getNamespaceCode())) {
            return;
        }
        String eventType = normalizeEventType(message.getEventType());
        if (REFRESH_EVENTS.contains(eventType)) {
            namespaceRuntimeLoader.refresh(message.getNamespaceCode(), message.getVersion());
            return;
        }
        if (DISABLE_EVENT.equals(eventType)) {
            namespaceRuntimeLoader.remove(message.getNamespaceCode(), ServiceInstanceRedisConstants.DISABLED);
            return;
        }
        if (DELETE_EVENT.equals(eventType)) {
            namespaceRuntimeLoader.remove(message.getNamespaceCode(), ServiceInstanceRedisConstants.OFFLINE);
            return;
        }
        log.warn("unsupported namespace change event, namespaceCode={}, eventType={}",
                message.getNamespaceCode(), message.getEventType());
    }

    private String normalizeEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return "";
        }
        return eventType.trim().toUpperCase(Locale.ROOT);
    }
}
