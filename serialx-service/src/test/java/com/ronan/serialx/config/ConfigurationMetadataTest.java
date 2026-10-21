package com.ronan.serialx.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 验证编译产物中的配置提示，避免只验证运行时绑定而遗漏 IDE 元数据。 */
class ConfigurationMetadataTest {
    @Test void generatesSnowflakePropertiesWithTypeAndDefaultValue() throws Exception {
        var resources = getClass().getClassLoader().getResources("META-INF/spring-configuration-metadata.json");
        Map<String, JsonNode> properties = new HashMap<>();
        var mapper = new ObjectMapper();
        while (resources.hasMoreElements()) {
            try (var input = resources.nextElement().openStream()) {
                mapper.readTree(input).path("properties").forEach(property ->
                        properties.put(property.path("name").asText(), property));
            }
        }
        var attempts = properties.get("serialx.service.snowflake.startup-attempts");
        assertNotNull(attempts, "编译产物必须包含 startup-attempts 配置元数据");
        assertEquals("java.lang.Integer", attempts.path("type").asText());
        assertEquals(3, attempts.path("defaultValue").asInt());
        assertEquals(SnowflakeNodeProperties.class.getName(), attempts.path("sourceType").asText());
        assertTrue(properties.containsKey("serialx.service.snowflake.admin-url"));
        assertTrue(properties.containsKey("serialx.service.snowflake.machine-token"));
        assertFalse(properties.containsKey("serialx.service.snowflake.renew-interval"));
        assertFalse(properties.containsKey("serialx.service.snowflake.safety-margin"));
        assertFalse(properties.containsKey("serialx.service.snowflake.expected-lease-duration"));
    }
}
