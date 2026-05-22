package com.ronan.serialx.sdk.transport;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.sdk.config.SerialxClientConfig;
import com.ronan.serialx.sdk.exception.SerialxHttpException;
import com.ronan.serialx.sdk.exception.SerialxRemoteException;
import com.ronan.serialx.sdk.exception.SerialxTimeoutException;
import com.ronan.serialx.sdk.model.NamespaceRuntimeStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

/**
 * SerialX HTTP 传输客户端。
 */
public class SerialxHttpClient {

    private static final String SUCCESS_CODE = "0";

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SerialxClientConfig config;

    public SerialxHttpClient(SerialxClientConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate(requestFactory(config.getConnectTimeout(), config.getReadTimeout()));
    }

    public String nextId(String endpoint, String namespaceCode, int attempts) {
        JsonNode data = post(endpoint, "/api/ids/next", "{\"namespaceCode\":\"" + escape(namespaceCode) + "\"}", namespaceCode, attempts);
        return data.path("id").asText();
    }

    public List<String> batchNextId(String endpoint, String namespaceCode, int count, int attempts) {
        String body = "{\"namespaceCode\":\"" + escape(namespaceCode) + "\",\"count\":" + count + "}";
        JsonNode data = post(endpoint, "/api/ids/batch", body, namespaceCode, attempts);
        JsonNode ids = data.path("ids");
        if (!ids.isArray()) {
            throw remote("服务端批量发号响应缺少 ids", namespaceCode, attempts, endpoint, null, null);
        }
        return objectMapper.convertValue(ids, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    public NamespaceRuntimeStatus getRuntimeStatus(String endpoint, String namespaceCode, int attempts) {
        String path = "/api/ids/runtime/" + UriUtils.encodePathSegment(namespaceCode, StandardCharsets.UTF_8);
        JsonNode data = exchange(endpoint, path, HttpMethod.GET, null, namespaceCode, attempts);
        return objectMapper.convertValue(data, NamespaceRuntimeStatus.class);
    }

    public boolean health(String endpoint) {
        try {
            exchange(endpoint, "/api/ids/health", HttpMethod.GET, null, null, 1);
            return true;
        } catch (SerialxHttpException | SerialxRemoteException | SerialxTimeoutException ex) {
            return false;
        }
    }

    private JsonNode post(String endpoint, String path, String body, String namespaceCode, int attempts) {
        return exchange(endpoint, path, HttpMethod.POST, body, namespaceCode, attempts);
    }

    private JsonNode exchange(
            String endpoint,
            String path,
            HttpMethod method,
            String body,
            String namespaceCode,
            int attempts) {
        HttpHeaders headers = headers();
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            // 这里仅负责协议调用和响应归一化；是否重试、是否降级节点由 DefaultSerialxClient 决策。
            ResponseEntity<String> response = restTemplate.exchange(URI.create(endpoint + path), method, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String code = root.path("code").asText();
            String message = root.path("message").asText();
            if (!SUCCESS_CODE.equals(code)) {
                throw remote("SerialX 服务端返回业务失败: " + code + " " + message,
                        namespaceCode, attempts, endpoint, code, message);
            }
            return root.get("data");
        } catch (HttpStatusCodeException ex) {
            throw new SerialxHttpException(
                    "SerialX HTTP 请求失败: " + ex.getStatusCode(),
                    ex,
                    namespaceCode,
                    attempts,
                    endpoint,
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString(),
                    config.getClientName(),
                    config.getClientId(),
                    config.getInstanceId());
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw new SerialxTimeoutException(
                        "SerialX 请求超时",
                        ex,
                        namespaceCode,
                        attempts,
                        endpoint,
                        config.getClientName(),
                        config.getClientId(),
                        config.getInstanceId());
            }
            throw new SerialxHttpException(
                    "SerialX 节点不可访问",
                    ex,
                    namespaceCode,
                    attempts,
                    endpoint,
                    null,
                    ex.getMessage(),
                    config.getClientName(),
                    config.getClientId(),
                    config.getInstanceId());
        } catch (SerialxRemoteException | SerialxHttpException | SerialxTimeoutException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SerialxHttpException(
                    "SerialX 响应解析失败",
                    ex,
                    namespaceCode,
                    attempts,
                    endpoint,
                    null,
                    ex.getMessage(),
                    config.getClientName(),
                    config.getClientId(),
                    config.getInstanceId());
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 客户端身份头只用于服务端日志、指标和排障，不参与鉴权，也不能作为可信身份依据。
        headers.set("X-SerialX-Client-Name", config.getClientName());
        headers.set("X-SerialX-Client-Id", config.getClientId());
        if (StringUtils.hasText(config.getClientVersion())) {
            headers.set("X-SerialX-Client-Version", config.getClientVersion());
        }
        headers.set("X-SerialX-Instance-Id", config.getInstanceId());
        headers.set("X-SerialX-Sdk-Version", SdkVersion.current());
        return headers;
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private SerialxRemoteException remote(
            String message,
            String namespaceCode,
            int attempts,
            String endpoint,
            String responseCode,
            String responseMessage) {
        return new SerialxRemoteException(
                message,
                namespaceCode,
                attempts,
                endpoint,
                responseCode,
                responseMessage,
                config.getClientName(),
                config.getClientId(),
                config.getInstanceId());
    }
}
