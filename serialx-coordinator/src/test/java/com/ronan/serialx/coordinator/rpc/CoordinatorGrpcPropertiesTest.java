package com.ronan.serialx.coordinator.rpc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoordinatorGrpcPropertiesTest {
    @TempDir Path directory;

    @Test
    void shouldNotRequireCertificateWhenDisabledAndFailClosedWhenEnabled() {
        var properties = new CoordinatorGrpcProperties();
        assertDoesNotThrow(properties::validate);
        properties.setEnabled(true);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void shouldRequireUniqueIdentityNodeBindingAndBoundedDeadline() throws Exception {
        var properties = new CoordinatorGrpcProperties();
        properties.setEnabled(true);
        Path placeholder = Files.createFile(directory.resolve("test-only-placeholder.pem"));
        properties.setCertificateChain(placeholder);
        properties.setPrivateKey(placeholder);
        properties.setTrustCertificate(placeholder);
        var identity = new CoordinatorGrpcProperties.MachineIdentity();
        identity.setCertificateUri("spiffe://serialx/service/node-a");
        identity.setRoles(Set.of(CoordinatorGrpcProperties.Role.NODE));
        properties.setIdentities(Map.of("node", identity));
        assertThrows(IllegalArgumentException.class, properties::validate);
        identity.setInstanceId("node-a");
        assertDoesNotThrow(properties::validate);
        properties.setIdentities(Map.of("first", identity, "second", identity));
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setIdentities(Map.of("node", identity));
        properties.setMaxRequestDuration(Duration.ofSeconds(6));
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
