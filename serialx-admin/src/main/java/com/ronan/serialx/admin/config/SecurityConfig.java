package com.ronan.serialx.admin.config;

import com.ronan.serialx.admin.security.JwtAuthenticationFilter;
import com.ronan.serialx.admin.support.AdminApiResponseWriter;
import com.ronan.serialx.common.enums.AdminUserRoleEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 后台 Spring Security 配置。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * JWT 认证过滤器。
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 安全过滤链的统一错误响应写入器。
     */
    private final AdminApiResponseWriter responseWriter;

    /**
     * 关闭 JWT 过滤器的 Servlet 自动注册，确保仅在后台安全链执行，不重复拦截机器接口。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        var registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 配置后台接口认证和授权规则。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/auth/login", "/actuator/health").permitAll()
                        .requestMatchers("/api/admin/users/**").hasRole(AdminUserRoleEnum.ADMIN.getCode())
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(configurer -> configurer
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.warn("admin authentication entry point triggered, uri={}, message={}", request.getRequestURI(), authException.getMessage());
                            responseWriter.writeFailure(response, 401, BusinessErrorCode.ADMIN_UNAUTHORIZED);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.warn("admin access denied handler triggered, uri={}, message={}", request.getRequestURI(), accessDeniedException.getMessage());
                            responseWriter.writeFailure(response, 403, BusinessErrorCode.ADMIN_FORBIDDEN);
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 提供密码哈希编码器。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
