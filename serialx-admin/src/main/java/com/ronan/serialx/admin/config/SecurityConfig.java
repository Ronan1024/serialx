package com.ronan.serialx.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.admin.security.JwtAuthenticationFilter;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
     * JSON 序列化组件。
     */
    private final ObjectMapper objectMapper;

    /**
     * 配置后台接口认证和授权规则。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/api/admin/auth/login", "/actuator/health").permitAll()
                        .requestMatchers("/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(configurer -> configurer
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.warn("admin authentication entry point triggered, uri={}, message={}",
                                    request.getRequestURI(), authException.getMessage());
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ApiResponse.fail(
                                    BusinessErrorCode.ADMIN_UNAUTHORIZED.getCode(),
                                    BusinessErrorCode.ADMIN_UNAUTHORIZED.getMessage()));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.warn("admin access denied handler triggered, uri={}, message={}",
                                    request.getRequestURI(), accessDeniedException.getMessage());
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ApiResponse.fail(
                                    BusinessErrorCode.ADMIN_FORBIDDEN.getCode(),
                                    BusinessErrorCode.ADMIN_FORBIDDEN.getMessage()));
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
