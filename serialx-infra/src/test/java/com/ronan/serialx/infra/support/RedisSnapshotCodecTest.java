package com.ronan.serialx.infra.support;

import com.ronan.serialx.common.model.ServiceInstanceSnapshot;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RedisSnapshotCodecTest {
    @Test void formatsDatesAndRoundTripsObjectsWithoutNullFields() {
        var snapshot = new ServiceInstanceSnapshot();
        snapshot.setInstanceId("svc-1");
        snapshot.setRuntimeId("runtime-1");
        snapshot.setLastReportTime(LocalDateTime.of(2026, 10, 13, 10, 20, 30, 123456789));
        var hash = RedisSnapshotCodec.toHash(snapshot);
        assertEquals("2026-10-13 10:20:30.123", hash.get("lastReportTime"));
        assertFalse(hash.containsKey("lastOfflineTime"));
        assertFalse(hash.containsKey("class"));
        var restored = RedisSnapshotCodec.fromHash(hash, ServiceInstanceSnapshot.class);
        assertEquals("runtime-1", restored.getRuntimeId());
        assertEquals(snapshot.getLastReportTime().truncatedTo(ChronoUnit.MILLIS), restored.getLastReportTime());
    }
    @Test void readsLegacyIsoDatesWithNanosecondsAndNumbers() {
        var restored = RedisSnapshotCodec.fromHash(Map.of("instanceId", "svc-1", "loadedVersion", "3",
                "lastReportTime", "2026-10-13T10:20:30.123456789", "unknownOldField", "ignored"), ServiceInstanceConfigStatusDO.class);
        assertEquals(3, restored.getLoadedVersion());
        assertEquals(123456789, restored.getLastReportTime().getNano());
        assertEquals("svc-1", restored.getInstanceId());
    }
    @Test void readsFixedFormatMissingFieldsAndBlankOptionalValues() {
        var restored = RedisSnapshotCodec.fromHash(Map.of("loadedVersion", "", "lastOfflineTime", " ",
                "lastReportTime", "2026-10-13 10:20:30.001"), ServiceInstanceConfigStatusDO.class);
        assertNull(restored.getLoadedVersion());
        assertNull(restored.getLastOfflineTime());
        assertEquals(1_000_000, restored.getLastReportTime().getNano());
    }
    @Test void rejectsMalformedDatesAndIntegerOverflow() {
        assertThrows(RuntimeException.class, () -> RedisSnapshotCodec.fromHash(
                Map.of("lastReportTime", "2026-02-30 10:20:30.000"), ServiceInstanceSnapshot.class));
        assertThrows(RuntimeException.class, () -> RedisSnapshotCodec.fromHash(
                Map.of("loadedVersion", "2147483648"), ServiceInstanceConfigStatusDO.class));
    }
}
