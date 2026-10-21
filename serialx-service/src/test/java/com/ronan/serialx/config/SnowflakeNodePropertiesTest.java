package com.ronan.serialx.config;

import com.ronan.serialx.infra.http.OutboundHttpProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class SnowflakeNodePropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({SnowflakeNodeProperties.class, OutboundHttpProperties.class})
    static class PropertiesConfig {
    }

    @Test void startupRejectsMissingMachineTokenWithoutManualValidation() {
        contextRunner.run(context -> assertInstanceOf(IllegalArgumentException.class,
                rootCause(context.getStartupFailure())));
    }

    @Test void validDefaultBudgetIsAccepted() {
        var properties = new SnowflakeNodeProperties(new OutboundHttpProperties());
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        assertDoesNotThrow(properties::validate);
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8080, http://localhost:8080",
            "http://localhost:8080/, http://localhost:8080",
            "http://localhost:8080////, http://localhost:8080",
            "http://localhost:8080/coordinator///, http://localhost:8080/coordinator",
            "http://localhost:8080/coordinator//v1///, http://localhost:8080/coordinator//v1",
            "https://coordinator.example:8443/control///, https://coordinator.example:8443/control",
            "http://[::1]:8080///, http://[::1]:8080"
    })
    void normalizesAdminUrlDuringValidationAndRemainsIdempotent(String adminUrl, String expected) {
        var properties = new SnowflakeNodeProperties(new OutboundHttpProperties());
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        properties.setAdminUrl(adminUrl);

        properties.validate();
        assertEquals(expected, properties.getAdminUrl());
        properties.validate();
        assertEquals(expected, properties.getAdminUrl());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "ftp://localhost", "http://", "https://", "http:///missing-host",
            "http://bad host", "http://localhost:abc", "http://localhost:0", "http://localhost:65536",
            "http://localhost/base?key=value", "http://localhost/base#fragment",
            "http://user:secret-password@localhost/base", "http://localhost/%ZZ"})
    void rejectsInvalidAdminBaseUrlBeforeAnyRequest(String adminUrl) {
        var properties = new SnowflakeNodeProperties(new OutboundHttpProperties());
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        properties.setAdminUrl(adminUrl);

        var failure = assertThrows(IllegalArgumentException.class, properties::validate);
        assertFalse(failure.getMessage().contains("secret-password"));
        assertNull(failure.getCause());
    }

    @Test void startupNormalizesBoundAdminUrlWithoutManualValidation() {
        contextRunner.withPropertyValues(
                "serialx.service.snowflake.machine-token=test-machine-token-of-32-bytes-or-longer",
                "serialx.service.snowflake.admin-url=https://coordinator.example/control///")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals("https://coordinator.example/control",
                            context.getBean(SnowflakeNodeProperties.class).getAdminUrl());
                });
    }

    @Test void startupRejectsMalformedAdminUrlBeforeGatewayCreation() {
        contextRunner.withPropertyValues(
                "serialx.service.snowflake.machine-token=test-machine-token-of-32-bytes-or-longer",
                "serialx.service.snowflake.admin-url=http://")
                .run(context -> assertInstanceOf(IllegalArgumentException.class,
                        rootCause(context.getStartupFailure())));
    }

    @Test void longInternalSlashRunDoesNotCauseSuperLinearValidation() {
        var properties = new SnowflakeNodeProperties(new OutboundHttpProperties());
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        var expected = "http://localhost:8080/" + "/".repeat(100_000) + "coordinator";
        properties.setAdminUrl(expected + "/");

        assertTimeout(Duration.ofSeconds(2), properties::validate);
        assertEquals(expected.length(), properties.getAdminUrl().length());
        assertTrue(properties.getAdminUrl().endsWith("coordinator"));
    }

    @Test void legacySafetyAndLeaseSettingsCannotOverrideSystemPolicy() {
        contextRunner.withPropertyValues(
                "serialx.service.snowflake.machine-token=test-machine-token-of-32-bytes-or-longer",
                "serialx.service.snowflake.safety-margin=1d",
                "serialx.service.snowflake.expected-lease-duration=1d")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var bean = new BeanWrapperImpl(context.getBean(SnowflakeNodeProperties.class));
                    assertFalse(bean.isWritableProperty("safetyMargin"));
                    assertFalse(bean.isWritableProperty("expectedLeaseDuration"));
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 11})
    void rejectsStartupAttemptsOutsideSystemBounds(int attempts) {
        var http = new OutboundHttpProperties();
        var properties = new SnowflakeNodeProperties(http);
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        properties.setStartupAttempts(attempts);
        var failure = assertThrows(IllegalArgumentException.class, properties::validate);
        assertTrue(failure.getMessage().contains("startup-attempts"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10})
    void acceptsStartupAttemptsAtSystemBounds(int attempts) {
        var properties = new SnowflakeNodeProperties(new OutboundHttpProperties());
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        properties.setStartupAttempts(attempts);
        assertDoesNotThrow(properties::validate);
    }

    @Test void legacyRenewalIntervalCannotOverrideSystemPolicy() {
        contextRunner.withPropertyValues(
                "serialx.service.snowflake.machine-token=test-machine-token-of-32-bytes-or-longer",
                "serialx.service.snowflake.renew-interval=1d")
                .run(context -> {
                    assertNull(context.getStartupFailure(), "旧周期配置应失效，而不是影响系统续租策略");
                    assertFalse(new BeanWrapperImpl(
                            context.getBean(SnowflakeNodeProperties.class)).isWritableProperty("renewInterval"));
                });
    }

    @Test void startupValidatesBoundPropertiesWithoutManualValidation() {
        contextRunner.withPropertyValues(
                "serialx.service.snowflake.machine-token=test-machine-token-of-32-bytes-or-longer",
                "serialx.service.snowflake.startup-attempts=2")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(2, context.getBean(SnowflakeNodeProperties.class).getStartupAttempts());
                });
    }

    @Test void startupRejectsInvalidRenewalBudgetWithoutManualValidation() {
        contextRunner.withPropertyValues(
                "serialx.service.snowflake.machine-token=test-machine-token-of-32-bytes-or-longer",
                "serialx.outbound.http.request-timeout=47s")
                .run(context -> assertInstanceOf(IllegalArgumentException.class,
                        rootCause(context.getStartupFailure())));
    }

    private Throwable rootCause(Throwable failure) {
        assertNotNull(failure, "非法配置必须导致上下文启动失败");
        while (failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    @Test void validatesCredentialsTimeoutAndRenewalBudget() {
        var httpProperties = new OutboundHttpProperties();
        var properties = new SnowflakeNodeProperties(httpProperties);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        assertDoesNotThrow(properties::validate);
        httpProperties.setRequestTimeout(Duration.ofSeconds(47));
        assertThrows(IllegalArgumentException.class, properties::validate);
        httpProperties.setRequestTimeout(Duration.ofMillis(500));
        assertThrows(IllegalArgumentException.class, properties::validate);
        httpProperties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setStartupAttempts(0);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test void acceptsBudgetJustBelowSystemLeaseDuration() {
        var http = new OutboundHttpProperties();
        http.setRequestTimeout(Duration.ofMillis(46_999));
        var properties = new SnowflakeNodeProperties(http);
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        assertDoesNotThrow(properties::validate);
    }

    @ParameterizedTest
    @ValueSource(longs = {47_000, 47_001})
    void rejectsBudgetAtOrAboveSystemLeaseDuration(long requestTimeoutMs) {
        var http = new OutboundHttpProperties();
        http.setRequestTimeout(Duration.ofMillis(requestTimeoutMs));
        var properties = new SnowflakeNodeProperties(http);
        properties.setMachineToken("test-machine-token-of-32-bytes-or-longer");
        var exception = assertThrows(IllegalArgumentException.class, properties::validate);
        assertTrue(exception.getMessage().contains("系统租期"));
    }

    @Test void startupUsesUnifiedHttpTimeoutForLeaseBudgetValidation() {
        contextRunner.withPropertyValues(
                "serialx.service.snowflake.machine-token=test-machine-token-of-32-bytes-or-longer",
                "serialx.outbound.http.connect-timeout=500ms",
                "serialx.outbound.http.request-timeout=48s")
                .run(context -> {
                    var failure = rootCause(context.getStartupFailure());
                    assertInstanceOf(IllegalArgumentException.class, failure);
                    assertTrue(failure.getMessage().contains("系统租期"));
                });
    }

    @Test void bindsUnifiedHttpTimeoutAlongsideNodeProperties() {
        contextRunner.withPropertyValues(
                "serialx.service.snowflake.machine-token=test-machine-token-of-32-bytes-or-longer",
                "serialx.outbound.http.connect-timeout=500ms",
                "serialx.outbound.http.request-timeout=3s")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(Duration.ofSeconds(3),
                            context.getBean(OutboundHttpProperties.class).getRequestTimeout());
                });
    }
}
