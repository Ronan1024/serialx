package com.ronan.serialx.application.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.enums.AppStatusEnum;
import com.ronan.serialx.common.util.AppSignatureUtils;
import com.ronan.serialx.api.filter.AppSignatureAuthFilter;
import com.ronan.serialx.config.AppSignatureAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 应用签名认证过滤器单元测试。
 */
class AppSignatureAuthFilterTest {

    private static final String APP_KEY = "orderApp";

    private static final String APP_SECRET = "test-secret-value";

    private AppSignatureAuthFilter filter;

    private AppSignatureAuthProperties properties;

    private StubAppCredentialService appCredentialService;

    @BeforeEach
    void setUp() {
        properties = new AppSignatureAuthProperties();
        properties.setEnabled(true);
        properties.setSignatureExpireWindow(Duration.ofMinutes(5));
        appCredentialService = new StubAppCredentialService();
        filter = new AppSignatureAuthFilter(properties, appCredentialService, new ObjectMapper());
    }

    @Test
    void shouldPassWhenSignatureIsValid() throws Exception {
        MockHttpServletRequest request = signedRequest(System.currentTimeMillis(), "{\"namespaceCode\":\"order_id\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void shouldRejectMissingSignature() throws Exception {
        MockHttpServletRequest request = request("{\"namespaceCode\":\"order_id\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        MockHttpServletRequest request = signedRequest(System.currentTimeMillis(), "{\"namespaceCode\":\"order_id\"}");
        request.removeHeader(AppSignatureAuthFilter.HEADER_SIGNATURE);
        request.addHeader(AppSignatureAuthFilter.HEADER_SIGNATURE, "bad-signature");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
    }

    @Test
    void shouldRejectDisabledApp() throws Exception {
        appCredentialService.appStatus = AppStatusEnum.DISABLED.getCode();
        MockHttpServletRequest request = signedRequest(System.currentTimeMillis(), "{\"namespaceCode\":\"order_id\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
    }

    @Test
    void shouldRejectExpiredTimestamp() throws Exception {
        MockHttpServletRequest request = signedRequest(
                System.currentTimeMillis() - Duration.ofMinutes(10).toMillis(), "{\"namespaceCode\":\"order_id\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
    }

    @Test
    void shouldSkipNonIdApiRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        request.setContent("{\"username\":\"admin\",\"password\":\"123456\"}".getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    private MockHttpServletRequest signedRequest(long timestamp, String body) {
        MockHttpServletRequest request = request(body);
        String nonce = "nonce-1";
        String secretHash = AppSignatureUtils.hashSecret(APP_SECRET);
        String canonicalString = AppSignatureUtils.canonicalString(
                "POST", "/api/ids/next", String.valueOf(timestamp), nonce, body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(AppSignatureAuthFilter.HEADER_APP_KEY, APP_KEY);
        request.addHeader(AppSignatureAuthFilter.HEADER_TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(AppSignatureAuthFilter.HEADER_NONCE, nonce);
        request.addHeader(
                AppSignatureAuthFilter.HEADER_SIGNATURE,
                AppSignatureUtils.signWithSecretHash(secretHash, canonicalString));
        return request;
    }

    private MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ids/next");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        return request;
    }

    private static class StubAppCredentialService implements AppCredentialService {

        private Integer appStatus = AppStatusEnum.ENABLED.getCode();

        @Override
        public AppCredential getByAppKey(String appKey) {
            return AppCredential.builder()
                    .appKey(appKey)
                    .appStatus(appStatus)
                    .secretStatus(AppStatusEnum.ENABLED.getCode())
                    .secretHash(AppSignatureUtils.hashSecret(APP_SECRET))
                    .build();
        }
    }
}
