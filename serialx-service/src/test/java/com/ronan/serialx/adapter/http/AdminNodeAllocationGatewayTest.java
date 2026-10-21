package com.ronan.serialx.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.node.NodeAllocationGateway;
import com.ronan.serialx.common.node.NodeAllocationException;
import com.ronan.serialx.common.node.NodeIdentityRequest;
import com.ronan.serialx.common.node.NodeLease;
import com.ronan.serialx.common.node.NodeRegisterRequest;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.config.SnowflakeNodeProperties;
import com.ronan.serialx.infra.http.OutboundHttpClient;
import com.ronan.serialx.infra.http.OutboundHttpProperties;
import com.ronan.serialx.support.LogCapture;
import ch.qos.logback.classic.Level;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AdminNodeAllocationGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboundHttpProperties httpProperties = new OutboundHttpProperties();
    private final SnowflakeNodeProperties properties = new SnowflakeNodeProperties(httpProperties);
    private OutboundHttpClient client;
    private final NodeLease lease = lease();
    private final CopyOnWriteArrayList<String> paths = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> tokens = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> bodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private volatile int status = 200;
    private volatile String response;
    private volatile long delayMs;
    private volatile boolean delayBody;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        response = mapper.writeValueAsString(ApiResponse.success(lease));
        properties.setAdminUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setMachineToken("test-machine-token-at-least-32-bytes");
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            tokens.add(exchange.getRequestHeaders().getFirst("X-SerialX-Service-Token"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMs > 0 && !delayBody) {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (status == 302) exchange.getResponseHeaders().set("Location", properties.getAdminUrl() + "/redirect");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                if (delayBody) {
                    exchange.getResponseBody().flush();
                    try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    private NodeAllocationGateway gateway() {
        properties.validate();
        client = new OutboundHttpClient(httpProperties);
        return new AdminNodeAllocationGateway(client, mapper, properties);
    }

    private NodeLease lease() {
        var result = new NodeLease();
        result.setRuntimeId("runtime");
        result.setDatacenterId(1);
        result.setWorkerId(2);
        result.setValidFromMs(100);
        result.setReservedUntilMs(200);
        result.setLeaseExpireAtMs(300);
        result.setLeaseDurationMs(60_000);
        result.setEpoch(1_704_067_200_000L);
        result.setWorkerIdBits(5);
        result.setDatacenterIdBits(5);
        result.setSequenceBits(12);
        return result;
    }

    @Test
    void postsAllOperationsWithMachineCredentialAndJsonBody() throws Exception {
        var gateway = gateway();
        var registration = new NodeRegisterRequest("runtime", "instance");
        var identity = new NodeIdentityRequest("runtime", 1, 2);
        assertEquals(lease, gateway.register(registration));
        assertEquals(lease, gateway.renew(identity));
        response = mapper.writeValueAsString(ApiResponse.success());
        gateway.release(identity);
        assertEquals(java.util.List.of("POST /internal/service-nodes/register", "POST /internal/service-nodes/renew",
                "POST /internal/service-nodes/release"), paths);
        assertTrue(tokens.stream().allMatch(properties.getMachineToken()::equals));
        assertEquals(mapper.readTree(mapper.writeValueAsString(registration)), mapper.readTree(bodies.getFirst()));
        assertEquals(mapper.readTree(mapper.writeValueAsString(identity)), mapper.readTree(bodies.get(1)));
    }

    @Test void logsHttpBoundariesWithoutCredentialsOrResponseBodies() {
        try (var logs = new LogCapture(AdminNodeAllocationGateway.class, Level.DEBUG)) {
            var gateway = gateway();
            gateway.register(new NodeRegisterRequest("runtime", "instance"));
            assertTrue(logs.contains(Level.DEBUG, "发送节点 HTTP 请求"));
            assertTrue(logs.contains(Level.DEBUG, "收到节点 HTTP 响应"));
            status = 503;
            response = "secret-response-" + properties.getMachineToken();
            assertThrows(NodeAllocationException.class, () -> gateway.renew(new NodeIdentityRequest("runtime", 1, 2)));
            assertFalse(logs.text().contains(properties.getMachineToken()));
            assertFalse(logs.text().contains("secret-response"));
            assertTrue(logs.text().contains("httpStatus=503"));
        }
    }

    @ParameterizedTest
    @CsvSource({"400,B10050006", "401,B10050007", "403,B10050007", "409,B10050002", "503,B10050003"})
    void classifiesNonSuccessHttpStatusWithoutExposingBody(int httpStatus, String code) {
        status = httpStatus;
        response = "secret-response-" + properties.getMachineToken();
        var gateway = gateway();
        var failure = assertThrows(NodeAllocationException.class,
                () -> gateway.register(new NodeRegisterRequest("runtime", "instance")));
        assertEquals(httpStatus, failure.getHttpStatus());
        assertEquals(code, failure.getCode());
        assertFalse(failure.toString().contains(properties.getMachineToken()));
        assertFalse(failure.toString().contains("secret-response"));
        assertNull(failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":\"0\",\"data\":null}", "{\"code\":\"error\"}", "{}", "null", "bad-json"})
    void refusesUnusableSuccessResponse(String body) {
        response = body;
        var gateway = gateway();
        assertThrows(NodeAllocationException.class,
                () -> gateway.renew(new NodeIdentityRequest("runtime", 1, 2)));
    }

    @Test
    void doesNotFollowRedirects() {
        status = 302;
        var gateway = gateway();
        assertThrows(NodeAllocationException.class,
                () -> gateway.register(new NodeRegisterRequest("runtime", "instance")));
        assertEquals(1, paths.size());
    }

    @Test
    void requestTimeoutIsBounded() {
        httpProperties.setConnectTimeout(Duration.ofMillis(10));
        httpProperties.setRequestTimeout(Duration.ofMillis(50));
        delayMs = 300;
        var gateway = gateway();
        assertTimeout(Duration.ofSeconds(2), () -> {
            var failure = assertThrows(NodeAllocationException.class,
                    () -> gateway.register(new NodeRegisterRequest("runtime", "instance")));
            assertEquals(503, failure.getHttpStatus());
        });
    }

    @Test
    void timeoutAlsoBoundsResponseBodyAfterHeadersArrive() {
        httpProperties.setConnectTimeout(Duration.ofMillis(10));
        httpProperties.setRequestTimeout(Duration.ofMillis(50));
        delayMs = 300;
        delayBody = true;
        var gateway = gateway();
        assertThrows(NodeAllocationException.class,
                () -> gateway.register(new NodeRegisterRequest("runtime", "instance")));
    }

    @Test
    void interruptionRestoresInterruptFlag() {
        var gateway = gateway();
        Thread.currentThread().interrupt();
        try {
            var failure = assertThrows(NodeAllocationException.class,
                    () -> gateway.register(new NodeRegisterRequest("runtime", "instance")));
            assertEquals(503, failure.getHttpStatus());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
