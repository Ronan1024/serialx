package com.ronan.serialx.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import com.ronan.serialx.sdk.model.EndpointHealth;
import com.ronan.serialx.sdk.model.NamespaceRuntimeStatus;
import org.junit.jupiter.api.Test;

/**
 * SerialX 默认客户端测试。
 */
class DefaultSerialxClientTest {

    @Test
    void shouldGenerateIdAndReportClientIdentityHeaders() throws Exception {
        try (TestHttpServer server = new TestHttpServer(false);
                SerialxClient client = baseBuilder(server.baseUrl()).cacheEnabled(false).build()) {

            String id = client.nextId("order_id");

            assertEquals("1", id);
            assertEquals("order-service", server.clientNames().getFirst());
        }
    }

    @Test
    void shouldParseRuntimeStatus() throws Exception {
        try (TestHttpServer server = new TestHttpServer(false);
                SerialxClient client = baseBuilder(server.baseUrl()).cacheEnabled(false).build()) {

            NamespaceRuntimeStatus status = client.getRuntimeStatus("order_id");

            assertEquals("order_id", status.getNamespaceCode());
            assertEquals(1, status.getVersion());
        }
    }

    @Test
    void shouldKeepCacheIsolatedByNamespace() throws Exception {
        try (TestHttpServer server = new TestHttpServer(false);
                SerialxClient client = baseBuilder(server.baseUrl())
                        .defaultPrefetchSize(2)
                        .lowWatermark(0)
                        .maxCacheSizePerNamespace(4)
                        .build()) {

            String orderId = client.nextId("order_id");
            String paymentId = client.nextId("payment_id");

            assertTrue(orderId.startsWith("order_id-"));
            assertTrue(paymentId.startsWith("payment_id-"));
        }
    }

    @Test
    void shouldFailoverToNextEndpointAndMarkFailedEndpointUnhealthy() throws Exception {
        try (TestHttpServer failed = new TestHttpServer(true);
                TestHttpServer healthy = new TestHttpServer(false);
                SerialxClient client = SerialxClient.builder()
                        .baseUrls(List.of(failed.baseUrl(), healthy.baseUrl()))
                        .clientName("order-service")
                        .clientId("order-service-prod")
                        .cacheEnabled(false)
                        .maxRetries(1)
                        .failureThreshold(1)
                        .healthCheckInterval(Duration.ofHours(1))
                        .build()) {

            String id = client.nextId("order_id");

            assertEquals("1", id);
            assertFalse(client.getEndpointStatuses().isEmpty());
            assertTrue(client.getEndpointStatuses().stream()
                    .anyMatch(status -> status.getUrl().equals(failed.baseUrl())
                            && status.getStatus() == EndpointHealth.UNHEALTHY));
        }
    }

    private SerialxClientBuilder baseBuilder(String baseUrl) {
        return SerialxClient.builder()
                .baseUrls(List.of(baseUrl))
                .clientName("order-service")
                .clientId("order-service-prod")
                .healthCheckInterval(Duration.ofHours(1));
    }
}
