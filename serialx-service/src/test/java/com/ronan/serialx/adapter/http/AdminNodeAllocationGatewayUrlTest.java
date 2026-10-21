package com.ronan.serialx.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.node.NodeIdentityRequest;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.config.SnowflakeNodeProperties;
import com.ronan.serialx.infra.http.OutboundHttpClient;
import com.ronan.serialx.infra.http.OutboundHttpProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.HttpResponse;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminNodeAllocationGatewayUrlTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboundHttpClient client = mock(OutboundHttpClient.class);
    private final SnowflakeNodeProperties properties = new SnowflakeNodeProperties(new OutboundHttpProperties());

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8080, http://localhost:8080",
            "http://localhost:8080/, http://localhost:8080",
            "http://localhost:8080////, http://localhost:8080",
            "http://localhost:8080/coordinator///, http://localhost:8080/coordinator",
            "http://localhost:8080/coordinator//v1///, http://localhost:8080/coordinator//v1"
    })
    @SuppressWarnings("unchecked")
    void usesValidatedAdminUrlWhenAppendingOperation(String adminUrl, String expectedBaseUrl) throws Exception {
        properties.setAdminUrl(adminUrl);
        properties.setMachineToken("test-machine-token-at-least-32-bytes");
        properties.validate();
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(mapper.writeValueAsString(ApiResponse.success()));
        when(client.execute(any(URI.class), eq("POST"), anyMap(), anyString())).thenReturn(response);

        var gateway = new AdminNodeAllocationGateway(client, mapper, properties);
        gateway.release(new NodeIdentityRequest("runtime", 1, 2));

        verify(client).execute(eq(URI.create(expectedBaseUrl + "/internal/service-nodes/release")),
                eq("POST"), anyMap(), anyString());
    }
}
