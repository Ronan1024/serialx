package com.ronan.serialx.core.snowflake;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class SnowflakeAvailabilityTest {
    @Test void healthChecksSafetyWithoutConsumingSequence() {
        var active = new AtomicBoolean(true);
        var generator = new SnowflakeGenerator(1, 1, () -> SnowflakeGenerator.EPOCH+1, () -> {
            if (!active.get()) throw new IllegalStateException("lost");
        });
        long first = generator.nextId();
        assertTrue(generator.isAvailable());
        assertEquals(first+1, generator.nextId());
        active.set(false);
        assertFalse(generator.isAvailable());
        assertThrows(IllegalStateException.class, generator::nextId);
    }
}
