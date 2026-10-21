package com.ronan.serialx.coordinator.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** 只校验独立连接预算，不启动数据库连接。 */
class CoordinatorPersistencePropertiesTest {
    @Test
    void shouldValidateCapacityAndHikariWaitBoundary() {
        var properties = new CoordinatorPersistenceProperties();
        assertDoesNotThrow(properties::validate);
        properties.setMaximumPoolSize(0);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setMaximumPoolSize(2);
        properties.setMinimumIdle(3);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setMinimumIdle(-1);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setMinimumIdle(0);
        properties.setConnectionTimeout(null);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setConnectionTimeout(Duration.ofMillis(249));
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setConnectionTimeout(Duration.ofMillis(250));
        assertDoesNotThrow(properties::validate);
    }
}
