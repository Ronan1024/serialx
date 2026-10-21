package com.ronan.serialx.infra.http;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 服务端 HTTP 出站入口，复用连接并统一完整响应超时与取消。
 * 请求头逐次传入，不保存业务凭证；业务错误映射及重试由调用方负责。
 */
@Component
public class OutboundHttpClient implements AutoCloseable {
    private final HttpClient client;
    private final Duration requestTimeout;

    public OutboundHttpClient(OutboundHttpProperties properties) {
        properties.validate();
        requestTimeout = properties.getRequestTimeout();
        client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }


    public HttpResponse<String> execute(URI uri, String method, Map<String, String> headers, String body)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(uri).timeout(requestTimeout);
        headers.forEach(builder::header);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var pending = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
        try {
            // 完整正文也必须在预算内，避免只限制响应头等待时间。
            return pending.get(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            throw new HttpTimeoutException("出站 HTTP 请求超时");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException failure) {
                throw failure;
            }
            throw new IOException("出站 HTTP 请求失败", ex.getCause());
        } finally {
            if (!pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        client.shutdownNow();
    }
}
