package com.ronan.serialx.admin.config;

import com.ronan.serialx.admin.security.ServiceNodeAuthenticationFilter;
import com.ronan.serialx.admin.support.AdminApiResponseWriter;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.coordinator.config.ServiceNodeAllocationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 内部节点兼容接口的机器身份认证配置，与后台 JWT 安全域隔离。
 * gRPC 的 NODE/DISCOVERY 权限仍由独立 TLS 端口校验，不复用此处身份。
 */
@Configuration
@RequiredArgsConstructor
public class ServiceNodeSecurityConfig {

    private static final String INTERNAL_NODE_API = "/internal/service-nodes/**";
    private static final String[] LEGACY_NODE_OPERATIONS = {
            "/internal/service-nodes/register",
            "/internal/service-nodes/renew",
            "/internal/service-nodes/release"
    };

    private final ServiceNodeAllocationProperties nodeAllocationProperties;
    private final AdminApiResponseWriter responseWriter;

    /**
     * 优先匹配内部节点路径：仅允许三个遗留 POST 操作，其他操作全部拒绝。
     * 即使兼容控制器关闭，此链仍会阻断内部路径；过滤器会逐请求检查迁移期限。
     */
    @Bean
    @Order(0)
    public SecurityFilterChain serviceNodeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(INTERNAL_NODE_API)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(configurer ->
                        configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(this::configureAuthorization)
                .exceptionHandling(this::configureExceptionHandling)
                .addFilterBefore(createAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers(HttpMethod.POST, LEGACY_NODE_OPERATIONS)
                .hasAuthority(ServiceNodeAuthenticationFilter.AUTHORITY)
                .anyRequest()
                .denyAll();
    }

    private void configureExceptionHandling(ExceptionHandlingConfigurer<HttpSecurity> configurer) {
        configurer
                .authenticationEntryPoint((request, response, exception) ->
                        responseWriter.writeFailure(response, HttpStatus.UNAUTHORIZED.value(),
                                BusinessErrorCode.NODE_AUTH_FAILED))
                .accessDeniedHandler((request, response, exception) ->
                        responseWriter.writeFailure(response, HttpStatus.FORBIDDEN.value(),
                                BusinessErrorCode.NODE_AUTH_FAILED));
    }

    private ServiceNodeAuthenticationFilter createAuthenticationFilter() {
        return new ServiceNodeAuthenticationFilter(nodeAllocationProperties, responseWriter);
    }
}
