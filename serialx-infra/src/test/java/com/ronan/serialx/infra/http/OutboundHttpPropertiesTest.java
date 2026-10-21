package com.ronan.serialx.infra.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class OutboundHttpPropertiesTest {
    @Test
    void validatesDefaultsAndInclusiveBoundaries() {
        var properties = new OutboundHttpProperties();
        assertDoesNotThrow(properties::validate);
        properties.setConnectTimeout(Duration.ofMillis(1));
        properties.setRequestTimeout(Duration.ofHours(1));
        assertDoesNotThrow(properties::validate);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PT0S", "PT-1S", "PT0.000999S", "PT1H0.001S", "PT9223372036854775807S"})
    void rejectsInvalidRequestDurationWithoutOverflow(String value) {
        var properties = new OutboundHttpProperties();
        properties.setRequestTimeout(value == null ? null : Duration.parse(value));
        var exception = assertThrows(IllegalArgumentException.class, properties::validate);
        assertTrue(exception.getMessage().contains("request-timeout"));
    }

    @Test
    void rejectsConnectTimeoutLongerThanRequest() {
        var properties = new OutboundHttpProperties();
        properties.setRequestTimeout(Duration.ofMillis(500));
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
