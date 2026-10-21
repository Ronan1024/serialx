package com.ronan.serialx.admin.security;

import com.ronan.serialx.coordinator.config.ServiceNodeAllocationProperties;
import com.ronan.serialx.admin.support.AdminApiResponseWriter;
import com.ronan.serialx.common.error.BusinessErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 仅由内部 SecurityFilterChain 创建，避免作为 Servlet Filter 重复注册。
 */
@Slf4j
public class ServiceNodeAuthenticationFilter extends OncePerRequestFilter {
    /**
     * 机器令牌请求头，不接受后台 Bearer JWT 替代。
     */
    public static final String HEADER = "X-SerialX-Service-Token";
    /**
     * 仅用于内部节点接口的权限，不授予后台用户权限。
     */
    public static final String AUTHORITY = "SERVICE_NODE";
    /**
     * 配置令牌的固定长度 SHA-256 摘要，用于等长比较，不在认证上下文存储令牌。
     */
    private final byte[] expectedDigest;
    /**
     * 迁移开关和截止时间每次请求检查，截止后不再接受共享令牌。
     */
    private final ServiceNodeAllocationProperties properties;
    /**
     * 以统一响应结构输出认证失败及真实 HTTP 状态。
     */
    private final AdminApiResponseWriter writer;

    /**
     * 由内部安全链显式构造，不注册为全局 Servlet Filter；启动时验证令牌配置。
     */
    public ServiceNodeAuthenticationFilter(ServiceNodeAllocationProperties properties, AdminApiResponseWriter writer) {
        properties.validate();
        this.properties = properties;
        this.expectedDigest = properties.isLegacyHttpEnabled() ? digest(properties.getMachineToken()) : new byte[0];
        this.writer = writer;
    }

    /**
     * 校验摘要后建立独立机器身份，认证失败清空上下文；具体可访问路径由安全链白名单限制。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        if (!properties.legacyHttpAvailable() || supplied == null || !MessageDigest.isEqual(expectedDigest, digest(supplied))) {
            log.warn("内部节点机器认证失败，httpStatus=401");
            SecurityContextHolder.clearContext();
            writer.writeFailure(response, 401, BusinessErrorCode.NODE_AUTH_FAILED);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("service-node", null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        SecurityContextHolder.setContext(context);
        log.debug("内部节点机器认证通过");
        chain.doFilter(request, response);
    }

    /**
     * 以 UTF-8 计算固定长度摘要，便于常量时间比较；摘要不是传输加密，部署仍需 HTTPS。
     */
    private byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
