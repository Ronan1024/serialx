package com.ronan.serialx.infra.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OutboundHttpClientTest {
    private HttpServer server;
    private OutboundHttpClient client;
    private final OutboundHttpProperties properties = new OutboundHttpProperties();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void close() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    @Test
    void reusesClientWithoutSharingRequestHeaders() throws Exception {
        var requests = new java.util.concurrent.CopyOnWriteArrayList<String>();
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-Token") + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        client = new OutboundHttpClient(properties);

        assertEquals("ok", client.execute(uri("/first?q=1"), "POST", Map.of("X-Token", "first"), "data").body());
        assertEquals(200, client.execute(uri("/second"), "GET", Map.of(), null).statusCode());

        assertEquals(java.util.List.of("POST /first?q=1 first data", "GET /second null "), requests);
    }

    @Test
    void doesNotFollowRedirects() throws Exception {
        server.createContext("/redirect", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Location", "/target");
                exchange.sendResponseHeaders(302, -1);
            }
        });
        client = new OutboundHttpClient(properties);
        assertEquals(302, client.execute(uri("/redirect"), "GET", Map.of(), null).statusCode());
    }

    @Test
    void timeoutIncludesBodyReadAfterHeaders() throws Exception {
        var headersSent = new CountDownLatch(1);
        var releaseBody = new CountDownLatch(1);
        server.createContext("/slow-body", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().flush();
                headersSent.countDown();
                try {
                    releaseBody.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                exchange.getResponseBody().write(new byte[]{'o', 'k'});
            }
        });
        properties.setConnectTimeout(Duration.ofMillis(100));
        properties.setRequestTimeout(Duration.ofMillis(500));
        client = new OutboundHttpClient(properties);

        var completed = new CompletableFuture<Throwable>();
        Thread caller = Thread.startVirtualThread(() -> {
            try {
                client.execute(uri("/slow-body"), "GET", Map.of(), null);
                completed.complete(null);
            } catch (Exception ex) {
                completed.complete(ex);
            }
        });
        try {
            assertTrue(headersSent.await(3, TimeUnit.SECONDS));
            assertInstanceOf(HttpTimeoutException.class, completed.get(3, TimeUnit.SECONDS));
        } finally {
            releaseBody.countDown();
            caller.join(3000);
        }
    }

    @Test
    void reportsInterruptionToCaller() {
        client = new OutboundHttpClient(properties);
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                    () -> client.execute(uri("/"), "GET", Map.of(), null));
        } finally {
            Thread.interrupted();
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
