package com.ronan.serialx.core.snowflake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeGeneratorTest {
    private static final long EPOCH = 1704067200000L;

    @Test
    void shouldKeepExistingBitLayoutAndIncrementSequence() {
        var generator = new SnowflakeGenerator(3, 2, () -> EPOCH + 100, () -> {});
        long expected = (100L << 22) | (2L << 17) | (3L << 12);
        assertEquals(expected, generator.nextId());
        assertEquals(expected + 1, generator.nextId());
    }

    @Test
    void shouldKeepMaximumNodeAndTimestampWithinPositiveLongRange() {
        var clock = new AtomicLong(EPOCH + (1L << 41) - 1);
        var generator = new SnowflakeGenerator(31, 31, clock::get, () -> {});

        assertEquals(Long.MAX_VALUE - 4095, generator.nextId());
        assertEquals(Long.MAX_VALUE - 4094, generator.nextId());
        clock.incrementAndGet();
        assertThrows(IllegalStateException.class, generator::nextId);
    }

    @Test
    void shouldResetSequenceOnlyWhenClockAdvances() {
        AtomicLong clock = new AtomicLong(EPOCH + 1);
        var generator = new SnowflakeGenerator(0, 0, clock::get, () -> {});
        assertEquals(1L << 22, generator.nextId());
        assertEquals((1L << 22) + 1, generator.nextId());
        clock.incrementAndGet();
        assertEquals(2L << 22, generator.nextId());
    }

    @Test
    void shouldWaitForNextMillisecondWhenSequenceIsExhausted() {
        AtomicInteger reads = new AtomicInteger();
        var generator = new SnowflakeGenerator(0, 0,
                () -> EPOCH + (reads.incrementAndGet() <= 4097 ? 1 : 2), () -> {});
        for (int sequence = 0; sequence < 4096; sequence++) {
            assertEquals((1L << 22) | sequence, generator.nextId());
        }
        assertEquals(2L << 22, generator.nextId());
    }

    @Test
    void shouldCheckSafetyWhileWaitingWithoutReusingSequenceAfterFailure() {
        AtomicInteger checks = new AtomicInteger();
        AtomicLong clock = new AtomicLong(EPOCH + 1);
        AtomicInteger failAt = new AtomicInteger(Integer.MAX_VALUE);
        var generator = new SnowflakeGenerator(0, 0, clock::get, () -> {
            if (checks.incrementAndGet() >= failAt.get()) {
                throw new IllegalStateException("not ready");
            }
        });
        for (int i = 0; i < 4096; i++) generator.nextId();
        failAt.set(checks.get() + 2);
        assertThrows(IllegalStateException.class, generator::nextId);
        failAt.set(Integer.MAX_VALUE);
        clock.incrementAndGet();
        assertEquals(2L << 22, generator.nextId());
    }

    @Test
    void shouldAbortSequenceWaitWhenInterrupted() {
        var generator = new SnowflakeGenerator(0, 0, () -> EPOCH + 1, () -> {});
        for (int i = 0; i < 4096; i++) generator.nextId();
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, generator::nextId);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "32,0", "0,-1", "0,32"})
    void shouldRejectInvalidNodeIds(long worker, long datacenter) {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeGenerator(worker, datacenter));
    }

    @Test
    void shouldRejectTimeBeforeEpochAndTimeOverflow() {
        AtomicLong clock = new AtomicLong(EPOCH - 1);
        var generator = new SnowflakeGenerator(0, 0, clock::get, () -> {});
        assertThrows(IllegalStateException.class, generator::nextId);
        clock.set(EPOCH + (1L << 41));
        assertThrows(IllegalStateException.class, generator::nextId);
        clock.decrementAndGet();
        assertTrue(generator.nextId() >= 0);
    }

    @Test
    void shouldRejectClockRollbackWithoutResettingSequence() {
        AtomicLong clock = new AtomicLong(EPOCH + 10);
        var generator = new SnowflakeGenerator(0, 0, clock::get, () -> {});
        long first = generator.nextId();
        clock.decrementAndGet();
        assertThrows(IllegalStateException.class, generator::nextId);
        clock.incrementAndGet();
        assertEquals(first + 1, generator.nextId());
    }

    @Test
    void shouldGenerateUniqueIdsUnderConcurrentCalls() throws Exception {
        var generator = new SnowflakeGenerator(1, 1, () -> EPOCH + 1, () -> {});
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Long>>();
            for (int i = 0; i < 2000; i++) futures.add(executor.submit(generator::nextId));
            var ids = new HashSet<Long>();
            for (var future : futures) assertTrue(ids.add(future.get()));
            assertEquals(2000, ids.size());
        }
    }
}
