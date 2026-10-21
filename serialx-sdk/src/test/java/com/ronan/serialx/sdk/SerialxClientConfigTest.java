package com.ronan.serialx.sdk;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.ronan.serialx.sdk.config.SerialxClientConfig;
import com.ronan.serialx.sdk.exception.SerialxConfigException;
import org.junit.jupiter.api.Test;

/**
 * SerialX SDK 配置测试。
 */
class SerialxClientConfigTest {

    @Test
    void shouldRejectMissingBaseUrls() {
        SerialxClientConfig config = new SerialxClientConfig();
        config.setClientName("order-service");
        config.setClientId("order-service-prod");

        assertThrows(SerialxConfigException.class, config::validateAndNormalize);
    }

    @Test
    void shouldRejectMissingClientName() {
        SerialxClientConfig config = new SerialxClientConfig();
        config.setBaseUrls(List.of("http://localhost:8080"));
        config.setClientId("order-service-prod");

        assertThrows(SerialxConfigException.class, config::validateAndNormalize);
    }

    @Test
    void shouldRejectInvalidCacheWatermark() {
        SerialxClientConfig config = new SerialxClientConfig();
        config.setBaseUrls(List.of("http://localhost:8080"));
        config.setClientName("order-service");
        config.setClientId("order-service-prod");
        config.setLowWatermark(1000);
        config.setMaxCacheSizePerNamespace(1000);

        assertThrows(SerialxConfigException.class, config::validateAndNormalize);
    }
}
