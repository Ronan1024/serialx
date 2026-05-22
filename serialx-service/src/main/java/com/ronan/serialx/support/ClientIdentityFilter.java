package com.ronan.serialx.support;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 解析 SDK 客户端观测身份，只用于日志和排障上下文。
 */
@Component
public class ClientIdentityFilter extends OncePerRequestFilter {

    private static final String ID_API_PATH = "/api/ids";

    private static final String ID_API_PREFIX = ID_API_PATH + "/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !ID_API_PATH.equals(requestUri) && !requestUri.startsWith(ID_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        ClientIdentity identity = ClientIdentity.builder()
                .clientName(header(request, ClientIdentity.HEADER_CLIENT_NAME))
                .clientId(header(request, ClientIdentity.HEADER_CLIENT_ID))
                .clientVersion(header(request, ClientIdentity.HEADER_CLIENT_VERSION))
                .instanceId(header(request, ClientIdentity.HEADER_INSTANCE_ID))
                .sdkVersion(header(request, ClientIdentity.HEADER_SDK_VERSION))
                .build();
        try {
            // 上报身份是观测字段，进入 ThreadLocal 和 MDC 方便日志串联，但不参与认证授权。
            ClientIdentityContext.set(identity);
            putMdc("serialxClientName", identity.getClientName());
            putMdc("serialxClientId", identity.getClientId());
            putMdc("serialxClientVersion", identity.getClientVersion());
            putMdc("serialxInstanceId", identity.getInstanceId());
            putMdc("serialxSdkVersion", identity.getSdkVersion());
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("serialxClientName");
            MDC.remove("serialxClientId");
            MDC.remove("serialxClientVersion");
            MDC.remove("serialxInstanceId");
            MDC.remove("serialxSdkVersion");
            ClientIdentityContext.clear();
        }
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void putMdc(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, value);
        }
    }
}
