package com.ronan.serialx.sdk;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * SDK 测试用轻量 HTTP 服务。
 */
class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    private final AtomicInteger sequence = new AtomicInteger();

    private final List<String> clientNames = new ArrayList<>();

    TestHttpServer(boolean failGenerate) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ids/next", exchange -> {
            recordHeaders(exchange);
            if (failGenerate) {
                write(exchange, 500, "{\"code\":\"E500\",\"message\":\"error\",\"data\":null}");
                return;
            }
            write(exchange, 200,
                    "{\"code\":\"0\",\"message\":\"success\",\"data\":{\"namespaceCode\":\"order_id\",\"version\":1,\"id\":\""
                            + sequence.incrementAndGet() + "\"}}");
        });
        server.createContext("/api/ids/batch", exchange -> {
            recordHeaders(exchange);
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String namespaceCode = body.contains("payment_id") ? "payment_id" : "order_id";
            int count = readCount(body);
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    ids.append(',');
                }
                ids.append('"').append(namespaceCode).append('-').append(sequence.incrementAndGet()).append('"');
            }
            write(exchange, 200,
                    "{\"code\":\"0\",\"message\":\"success\",\"data\":{\"namespaceCode\":\"" + namespaceCode
                            + "\",\"version\":1,\"ids\":[" + ids + "]}}");
        });
        server.createContext("/api/ids/runtime/order_id", exchange -> write(exchange, 200,
                "{\"code\":\"0\",\"message\":\"success\",\"data\":{\"namespaceCode\":\"order_id\",\"version\":1,\"idMode\":1,\"runtimeStatus\":\"ACTIVE\"}}"));
        server.createContext("/api/ids/health", exchange -> write(exchange, 200,
                "{\"code\":\"0\",\"message\":\"success\",\"data\":{\"available\":true,\"nodeId\":\"node-1\",\"loadedNamespaceCount\":1,\"timestamp\":1}}"));
        server.setExecutor(Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "serialx-sdk-test-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<String> clientNames() {
        return clientNames;
    }

    private void recordHeaders(HttpExchange exchange) {
        clientNames.add(exchange.getRequestHeaders().getFirst("X-SerialX-Client-Name"));
    }

    private int readCount(String body) {
        int index = body.indexOf("\"count\":");
        if (index < 0) {
            return 1;
        }
        int start = index + "\"count\":".length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Integer.parseInt(body.substring(start, end));
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
