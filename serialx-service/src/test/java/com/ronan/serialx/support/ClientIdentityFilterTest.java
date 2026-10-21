package com.ronan.serialx.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * SDK 客户端观测身份过滤器测试。
 */
class ClientIdentityFilterTest {

    @Test
    void shouldParseClientIdentityHeadersForIdApi() throws Exception {
        ClientIdentityFilter filter = new ClientIdentityFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ids/next");
        request.addHeader(ClientIdentity.HEADER_CLIENT_NAME, "order-service");
        request.addHeader(ClientIdentity.HEADER_CLIENT_ID, "order-service-prod");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> {
            ClientIdentity identity = ClientIdentityContext.get().orElseThrow();
            assertEquals("order-service", identity.getClientName());
            assertEquals("order-service-prod", identity.getClientId());
            invoked.set(true);
        };

        filter.doFilter(request, response, chain);

        assertTrue(invoked.get());
        assertTrue(ClientIdentityContext.get().isEmpty());
    }

    @Test
    void shouldSkipNonIdApi() throws Exception {
        ClientIdentityFilter filter = new ClientIdentityFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }
}
