package com.ronan.serialx.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ronan.serialx.config.ServiceInstanceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Service 实例身份解析测试。
 */
class ServiceInstanceIdentityTest {

    @Test
    void shouldNotCacheUnboundPortOrShareRandomPortIdentity() {
        var properties = new ServiceInstanceProperties();
        properties.setAdvertisedIp("127.0.0.1");
        var environment = new MockEnvironment().withProperty("server.port", "0");
        var first = new ServiceInstanceIdentity(properties, environment);
        var second = new ServiceInstanceIdentity(properties, environment);
        String stableId = first.getInstanceId();
        org.junit.jupiter.api.Assertions.assertNotEquals(stableId, second.getInstanceId());
        assertEquals("0", first.getPort());
        environment.setProperty("local.server.port", "43999");
        assertEquals("43999", first.getPort());
        assertEquals(stableId, first.getInstanceId());
    }

    @Test
    void shouldBuildDefaultInstanceIdWithIpAndPort() {
        ServiceInstanceProperties properties = new ServiceInstanceProperties();
        properties.setAdvertisedIp("192.168.1.10");
        ServiceInstanceIdentity identity = new ServiceInstanceIdentity(
                properties,
                new MockEnvironment().withProperty("server.port", "8081"));

        assertEquals("serialx-service-192.168.1.10-8081", identity.getInstanceId());
        assertEquals("serialx-service/192.168.1.10:8081", identity.getHost());
    }

    @Test
    void shouldPreferConfiguredInstanceId() {
        ServiceInstanceProperties properties = new ServiceInstanceProperties();
        properties.setInstanceId("custom-node");
        properties.setAdvertisedIp("192.168.1.10");
        ServiceInstanceIdentity identity = new ServiceInstanceIdentity(
                properties,
                new MockEnvironment().withProperty("server.port", "8081"));

        assertEquals("custom-node", identity.getInstanceId());
    }
}
