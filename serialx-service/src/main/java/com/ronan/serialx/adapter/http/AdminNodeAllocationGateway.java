package com.ronan.serialx.adapter.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.NodeAllocationException;
import com.ronan.serialx.common.node.NodeAllocationGateway;
import com.ronan.serialx.common.node.NodeIdentityRequest;
import com.ronan.serialx.common.node.NodeLease;
import com.ronan.serialx.common.node.NodeRegisterRequest;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.config.SnowflakeNodeProperties;
import com.ronan.serialx.infra.http.OutboundHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 独立 service 的节点控制面客户端；失败不能解释为已获得或延长租约。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Profile("!standalone")
public class AdminNodeAllocationGateway implements NodeAllocationGateway {
    private static final TypeReference<ApiResponse<NodeLease>> LEASE_RESPONSE = new TypeReference<>() {};

    private final OutboundHttpClient client;
    private final ObjectMapper mapper;
    private final SnowflakeNodeProperties properties;

    @Override
    public NodeLease register(NodeRegisterRequest request) {
        return post("register", request, true);
    }

    @Override
    public NodeLease renew(NodeIdentityRequest request) {
        return post("renew", request, true);
    }

    @Override
    public void release(NodeIdentityRequest request) {
        post("release", request, false);
    }

    private NodeLease post(String operation, Object body, boolean requireLease) {
        long started = System.nanoTime();
        log.debug("发送节点 HTTP 请求，operation={}", operation);
        try {
            URI uri = URI.create(properties.getAdminUrl() + "/internal/service-nodes/" + operation);
            Map<String, String> handler = Map.of("Content-Type", "application/json", "X-SerialX-Service-Token", properties.getMachineToken());
            HttpResponse<String> response = client.execute(uri, "POST", handler, mapper.writeValueAsString(body));
            log.debug("收到节点 HTTP 响应，operation={}, httpStatus={}, elapsedMs={}",
                    operation, response.statusCode(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw httpFailure(response.statusCode());
            }
            ApiResponse<NodeLease> result = mapper.readValue(response.body(), LEASE_RESPONSE);
            if (result == null || !"0".equals(result.getCode()) || (requireLease && result.getData() == null)) {
                throw httpFailure(503);
            }
            return result.getData();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("节点 HTTP 请求被中断，operation={}", operation);
            throw httpFailure(503);
        } catch (IOException e) {
            // 响应正文和底层异常可能包含凭证，不传播到日志或调用方。
            log.debug("节点 HTTP 请求或响应解析失败，operation={}, exceptionType={}", operation, e.getClass().getSimpleName());
            throw httpFailure(503);
        } catch (IllegalArgumentException e) {
            log.debug("节点 HTTP 请求参数无效，operation={}, exceptionType={}", operation, e.getClass().getSimpleName());
            throw httpFailure(400);
        }
    }

    private static NodeAllocationException httpFailure(int status) {
        BusinessErrorCode code = switch (status) {
            case 400 -> BusinessErrorCode.NODE_PARAM_INVALID;
            case 401, 403 -> BusinessErrorCode.NODE_AUTH_FAILED;
            case 409 -> BusinessErrorCode.NODE_LEASE_LOST;
            default -> BusinessErrorCode.NODE_NOT_READY;
        };
        return new NodeAllocationException(code, status);
    }
}
