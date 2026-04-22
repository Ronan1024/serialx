package com.ronan.serialx.admin.security;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.common.enums.AdminUserStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.infra.entity.AdminUserDO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer Token 认证过滤器。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 认证请求头名称。
     */
    private static final String AUTHORIZATION = "Authorization";

    /**
     * Bearer Token 前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * JWT 管理组件。
     */
    private final JwtTokenManager jwtTokenManager;

    /**
     * 后台用户数据访问服务。
     */
    private final AdminUserService adminUserService;

    /**
     * JSON 序列化组件。
     */
    private final ObjectMapper objectMapper;

    /**
     * 从请求头解析 JWT 并写入 Spring Security 上下文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AdminUserPrincipal principal = jwtTokenManager.parse(authorization.substring(BEARER_PREFIX.length()));
            AdminUserDO user = adminUserService.getById(principal.getUserId());
            if (user == null || !AdminUserStatusEnum.isEnabled(user.getStatus())) {
                throw BizException.unauthorized(
                        BusinessErrorCode.ADMIN_TOKEN_INVALID, BusinessErrorCode.ADMIN_TOKEN_INVALID.getMessage());
            }
            principal = new AdminUserPrincipal(user.getId(), user.getUsername(), user.getRoles());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (BizException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.fail(ex.getCode(), ex.getMessage()));
        }
    }
}
