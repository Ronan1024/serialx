package com.ronan.serialx.admin.config;

import com.ronan.serialx.coordinator.config.ServiceNodeAllocationProperties;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ServiceNodeAllocationPropertiesTest {
    @Test
    void shouldAllowCertificateOnlyModeAndRequireExplicitSunset() {
        var properties = new ServiceNodeAllocationProperties();
        properties.setLegacyHttpEnabled(false);
        assertDoesNotThrow(properties::validate);
        assertFalse(properties.legacyHttpAvailable());
        properties.setLegacyHttpUntil(null);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void shouldCheckSunsetAtEachRequestAndNeverIncludeTokenInToString() {
        var properties = new ServiceNodeAllocationProperties();
        properties.setMachineToken("test-only-machine-token-32-bytes-long");
        properties.setLegacyHttpUntil(java.time.Instant.now().plusSeconds(60));
        assertTrue(properties.legacyHttpAvailable());
        properties.setLegacyHttpUntil(java.time.Instant.EPOCH);
        assertFalse(properties.legacyHttpAvailable());
        assertFalse(properties.toString().contains(properties.getMachineToken()));
    }

    @Test
    void shouldRequireMachineCredentialAndSafeWindow() {
        var properties = new ServiceNodeAllocationProperties();
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setMachineToken("short");
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setMachineToken("test-only-machine-token-32-bytes-long");
        assertDoesNotThrow(properties::validate);
        properties.setReservedWindow(Duration.ofSeconds(59));
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setReservedWindow(Duration.ofSeconds(60));
        properties.setReservedWindow(null);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
