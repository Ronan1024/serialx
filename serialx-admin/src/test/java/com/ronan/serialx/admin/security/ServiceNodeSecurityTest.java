package com.ronan.serialx.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.admin.config.AdminSecurityProperties;
import com.ronan.serialx.admin.config.SecurityConfig;
import com.ronan.serialx.admin.config.ServiceNodeSecurityConfig;
import com.ronan.serialx.coordinator.config.ServiceNodeAllocationProperties;
import com.ronan.serialx.admin.controller.ServiceNodeRegistrationController;
import com.ronan.serialx.admin.handler.ApiResponseBodyAdvice;
import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.admin.support.AdminApiResponseWriter;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.NodeAllocationException;
import com.ronan.serialx.common.node.NodeLease;
import com.ronan.serialx.infra.entity.AdminUserDO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ExtendWith(OutputCaptureExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {ServiceNodeSecurityTest.TestConfig.class, SecurityConfig.class,
        ServiceNodeSecurityConfig.class,
        ServiceNodeRegistrationController.class, ApiResponseBodyAdvice.class})
class ServiceNodeSecurityTest {
    private static final String TOKEN = "test-only-machine-token-32-bytes-long";
    private static final String BODY = "{\"runtimeId\":\"01234567-89ab-cdef-0123-456789abcdef\",\"instanceId\":\"svc\"}";
    @Autowired private WebApplicationContext context;
    @Autowired private SnowflakeNodeManager manager;
    @Autowired private JwtTokenManager tokens;
    @Autowired private AdminUserService users;
    @Autowired private ServiceNodeAllocationProperties nodeProperties;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(manager);
        reset(users);
        nodeProperties.setLegacyHttpEnabled(true);
        nodeProperties.setLegacyHttpUntil(java.time.Instant.now().plusSeconds(3600));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test void shouldLogRejectedCredentialsWithoutHeaderValues(CapturedOutput output) throws Exception {
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", "secret-invalid-header")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        assertTrue(output.getAll().contains("内部节点机器认证失败"));
        assertFalse(output.getAll().contains("secret-invalid-header"));
        assertFalse(output.getAll().contains(TOKEN));
    }

    @Test void shouldLogReceivedRequestMetadataAtDebug(CapturedOutput output) throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ServiceNodeRegistrationController.class);
        var previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            when(manager.register(any())).thenReturn(lease());
            mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
            assertTrue(output.getAll().contains("收到节点注册请求"));
            assertTrue(output.getAll().contains("runtimeId=01234567-89ab-cdef-0123-456789abcdef"));
            assertFalse(output.getAll().contains(TOKEN));
            assertFalse(output.getAll().contains(BODY));
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private NodeLease lease() {
        var lease = new NodeLease();
        lease.setRuntimeId("01234567-89ab-cdef-0123-456789abcdef");
        lease.setDatacenterId(1);
        lease.setWorkerId(2);
        lease.setValidFromMs(1704067200000L);
        lease.setReservedUntilMs(1704067260000L);
        lease.setLeaseExpireAtMs(1704067260000L);
        lease.setLeaseDurationMs(60000);
        lease.setEpoch(1704067200000L);
        lease.setWorkerIdBits(5);
        lease.setDatacenterIdBits(5);
        lease.setSequenceBits(12);
        return lease;
    }

    @Test
    void shouldRejectMissingWrongTokenAndAdminJwt() throws Exception {
        mvc.perform(post("/internal/service-nodes/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(BusinessErrorCode.NODE_AUTH_FAILED.getCode()));
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        String jwt = tokens.issueToken(new AdminUserPrincipal(1L, "admin", "ADMIN"));
        mvc.perform(post("/internal/service-nodes/register").header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(manager);
    }

    @Test
    void shouldAcceptMachineTokenAndIgnoreJwtOnInternalPaths() throws Exception {
        when(manager.register(any())).thenReturn(lease());
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .header("Authorization", "Bearer invalid-admin-jwt").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.workerId").value(2));
        verify(manager, times(1)).register(any());
    }

    @Test
    void shouldRejectInvalidBodyAndMissingNodeCoordinates() throws Exception {
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"runtimeId\":\"bad\",\"instanceId\":\"svc\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(BusinessErrorCode.NODE_PARAM_INVALID.getCode()));
        mvc.perform(post("/internal/service-nodes/renew").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"runtimeId\":\"01234567-89ab-cdef-0123-456789abcdef\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(manager);
    }

    @Test
    void shouldReturnNonSuccessStatusForAllocationFailure() throws Exception {
        when(manager.register(any())).thenThrow(new NodeAllocationException(BusinessErrorCode.NODE_POOL_EXHAUSTED, 503));
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(BusinessErrorCode.NODE_POOL_EXHAUSTED.getCode()));
        doThrow(new NodeAllocationException(BusinessErrorCode.NODE_LEASE_LOST, 409)).when(manager).register(any());
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
    }

    @Test
    void shouldExposeRenewAndReleaseWithSingleResponseWrapping() throws Exception {
        String body = "{\"runtimeId\":\"01234567-89ab-cdef-0123-456789abcdef\",\"datacenterId\":1,\"workerId\":2}";
        when(manager.renew(any())).thenReturn(lease());
        mvc.perform(post("/internal/service-nodes/renew").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.workerId").value(2));
        mvc.perform(post("/internal/service-nodes/release").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
        verify(manager).renew(any());
        verify(manager).release(any());
    }

    @Test
    void shouldReturnUnavailableForDatabaseFailureAndBadRequestForMalformedJson() throws Exception {
        when(manager.register(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("test database unavailable"));
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(BusinessErrorCode.NODE_NOT_READY.getCode()));
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("not-json")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepAdminJwtAuthenticationWorking() throws Exception {
        AdminUserDO admin = new AdminUserDO();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRoles("ADMIN");
        admin.setStatus(1);
        when(users.getById(1L)).thenReturn(admin);
        String jwt = tokens.issueToken(new AdminUserPrincipal(1L, "admin", "ADMIN"));
        mvc.perform(get("/api/admin/node-test").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
        verify(users, times(1)).getById(1L);
    }

    @Test
    void shouldReturnNonSuccessStatusForTransactionAndUnexpectedFailures() throws Exception {
        doThrow(new org.springframework.transaction.TransactionTimedOutException("test timeout")).when(manager).register(any());
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isServiceUnavailable());
        doThrow(new IllegalStateException("test unexpected error")).when(manager).register(any());
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isInternalServerError());
    }

    @Test
    void shouldNotAuthorizeAdminPathsWithMachineTokenOrUnknownInternalOperations() throws Exception {
        mvc.perform(get("/api/admin/namespaces/1").header("X-SerialX-Service-Token", TOKEN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/service-nodes/unknown").header("X-SerialX-Service-Token", TOKEN)).andExpect(status().isForbidden());
        verifyNoInteractions(manager);
    }

    @Test
    void shouldDisableServletRegistrationForSecurityFilters() {
        assertFalse(context.getBean("jwtFilterRegistration", FilterRegistrationBean.class).isEnabled());
        assertTrue(context.getBeansOfType(ServiceNodeAuthenticationFilter.class).isEmpty());
    }

    /** 截止后或显式停用时，共享令牌不再获得节点权限，且不能落入后台宽松路径。 */
    @Test
    void shouldRejectLegacyCredentialAfterSunsetOrDisable() throws Exception {
        nodeProperties.setLegacyHttpUntil(java.time.Instant.EPOCH);
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        nodeProperties.setLegacyHttpUntil(java.time.Instant.now().plusSeconds(3600));
        nodeProperties.setLegacyHttpEnabled(false);
        mvc.perform(post("/internal/service-nodes/register").header("X-SerialX-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(manager);
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class TestConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean AdminApiResponseWriter writer(ObjectMapper mapper) { return new AdminApiResponseWriter(mapper); }
        @Bean AdminSecurityProperties adminProperties() {
            var properties = new AdminSecurityProperties();
            properties.setJwtSecret("test-only-admin-jwt-secret-32-bytes-long");
            return properties;
        }
        @Bean ServiceNodeAllocationProperties nodeProperties() {
            var properties = new ServiceNodeAllocationProperties();
            properties.setMachineToken(TOKEN);
            return properties;
        }
        @Bean JwtTokenManager tokens(AdminSecurityProperties properties) { return new JwtTokenManager(properties); }
        @Bean AdminUserService users() { return mock(AdminUserService.class); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtTokenManager tokens, AdminUserService users, AdminApiResponseWriter writer) {
            return new JwtAuthenticationFilter(tokens, users, writer);
        }
        @Bean SnowflakeNodeManager manager() { return mock(SnowflakeNodeManager.class); }
        @Bean AdminProbeController probe() { return new AdminProbeController(); }
    }

    @RestController
    static class AdminProbeController {
        @GetMapping("/api/admin/node-test")
        public java.util.Map<String, String> probe() { return java.util.Map.of("status", "ok"); }
    }
}
