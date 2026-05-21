package com.ronan.serialx.api.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.enums.AppStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.common.util.AppSignatureUtils;
import com.ronan.serialx.config.AppSignatureAuthProperties;
import com.ronan.serialx.application.auth.AppCredential;
import com.ronan.serialx.application.auth.AppCredentialService;
import com.ronan.serialx.support.CachedBodyHttpServletRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 应用签名认证过滤器。
 */
@Component
@RequiredArgsConstructor
public class AppSignatureAuthFilter extends OncePerRequestFilter {

    private static final String ID_API_PATH = "/api/ids";

    private static final String ID_API_PREFIX = ID_API_PATH + "/";

    public static final String HEADER_APP_KEY = "X-App-Key";

    public static final String HEADER_TIMESTAMP = "X-Timestamp";

    public static final String HEADER_NONCE = "X-Nonce";

    public static final String HEADER_SIGNATURE = "X-Signature";

    private final AppSignatureAuthProperties properties;

    private final AppCredentialService appCredentialService;

    private final ObjectMapper objectMapper;

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
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        if (!verify(request, body)) {
            writeAuthFailedResponse(response);
            return;
        }
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private boolean verify(HttpServletRequest request, byte[] body) {
        String appKey = request.getHeader(HEADER_APP_KEY);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);
        if (!StringUtils.hasText(appKey)
                || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature)) {
            return false;
        }
        if (isExpired(timestamp)) {
            return false;
        }
        AppCredential credential = appCredentialService.getByAppKey(appKey);
        if (credential == null
                || !AppStatusEnum.ENABLED.getCode().equals(credential.getAppStatus())
                || !AppStatusEnum.ENABLED.getCode().equals(credential.getSecretStatus())
                || !StringUtils.hasText(credential.getSecretHash())) {
            return false;
        }
        String canonicalString = AppSignatureUtils.canonicalString(
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                nonce,
                body);
        String expectedSignature = AppSignatureUtils.signWithSecretHash(credential.getSecretHash(), canonicalString);
        return AppSignatureUtils.signatureEquals(expectedSignature, signature);
    }

    private boolean isExpired(String timestampText) {
        try {
            long timestamp = Long.parseLong(timestampText);
            long diff = Math.abs(Instant.now().toEpochMilli() - timestamp);
            return diff > properties.getSignatureExpireWindow().toMillis();
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    private void writeAuthFailedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail(
                BusinessErrorCode.APP_AUTH_FAILED.getCode(),
                BusinessErrorCode.APP_AUTH_FAILED.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
