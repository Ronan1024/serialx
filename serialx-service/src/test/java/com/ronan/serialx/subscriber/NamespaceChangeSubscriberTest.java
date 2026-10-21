package com.ronan.serialx.subscriber;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.application.refresh.NamespaceRuntimeLoader;
import com.ronan.serialx.common.model.NamespaceChangeMessage;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamespaceChangeSubscriberTest {
    @Test void logsReceivedEventAndSuccessfulHandlingWithoutRawPayload() throws Exception {
        var mapper = new ObjectMapper();
        var loader = mock(NamespaceRuntimeLoader.class);
        var subscriber = new NamespaceChangeSubscriber(mapper, loader, new ServiceInstanceProperties());
        var change = NamespaceChangeMessage.builder().namespaceCode("order_id").version(2).eventType("PUBLISH").build();
        try (var logs = new LogCapture(NamespaceChangeSubscriber.class, Level.DEBUG)) {
            subscriber.onLocalNamespaceChange(change);
            subscriber.onMessage(new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8),
                    mapper.writeValueAsBytes(change)), null);
            assertTrue(logs.contains(Level.DEBUG, "收到本地 Namespace 变更"));
            assertTrue(logs.contains(Level.DEBUG, "收到 Redis Namespace 变更"));
            assertTrue(logs.contains(Level.DEBUG, "Namespace 变更处理完成"));
            assertTrue(logs.text().contains("namespaceCode=order_id"));
            assertTrue(logs.text().contains("version=2"));
        }
        verify(loader, times(2)).refresh("order_id", 2);
    }

    @Test void malformedMessageLogsOnlySafeFailureMetadata() {
        var subscriber = new NamespaceChangeSubscriber(new ObjectMapper(), mock(NamespaceRuntimeLoader.class),
                new ServiceInstanceProperties());
        try (var logs = new LogCapture(NamespaceChangeSubscriber.class, Level.DEBUG)) {
            subscriber.onMessage(new DefaultMessage(new byte[0], "secret-machine-token-invalid-json".getBytes(StandardCharsets.UTF_8)), null);
            assertTrue(logs.contains(Level.WARN, "Namespace 变更处理失败"));
            assertFalse(logs.text().contains("secret-machine-token-invalid-json"));
        }
    }
}
