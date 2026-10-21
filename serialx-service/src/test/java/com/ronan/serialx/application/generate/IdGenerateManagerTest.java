package com.ronan.serialx.application.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import com.ronan.serialx.application.stats.CallStatsRecord;
import com.ronan.serialx.application.stats.CallStatsRecorder;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.api.dto.IdBatchGenerateResponse;
import com.ronan.serialx.api.dto.IdGenerateResponse;
import com.ronan.serialx.api.dto.NamespaceRuntimeStatusResponse;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.domain.runtime.SnowflakeNamespaceRuntime;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.infra.entity.IdCallStatsMinuteDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ID 生成业务编排单元测试。
 */
class IdGenerateManagerTest {

    private NamespaceRegistry namespaceRegistry;

    private FakeCallStatsRecorder callStatsRecorder;

    private IdGenerateManager idGenerateManager;

    @BeforeEach
    void setUp() {
        namespaceRegistry = new NamespaceRegistry();
        callStatsRecorder = new FakeCallStatsRecorder();
        idGenerateManager = new IdGenerateManager(namespaceRegistry, callStatsRecorder);
    }

    @Test
    void shouldGenerateNextId() {
        namespaceRegistry.replace("order_id", buildSnowflakeRuntime());

        IdGenerateResponse response = idGenerateManager.nextId("order_id");

        assertEquals("order_id", response.getNamespaceCode());
        assertEquals(3, response.getVersion());
    }

    @Test
    void shouldGenerateBatchIds() {
        namespaceRegistry.replace("order_id", buildSnowflakeRuntime());

        IdBatchGenerateResponse response = idGenerateManager.batchNextId("order_id", 3);

        assertEquals("order_id", response.getNamespaceCode());
        assertEquals(3, response.getVersion());
        assertEquals(3, response.getIds().size());
    }

    @Test
    void shouldQueryRuntimeStatus() {
        namespaceRegistry.replace("order_id", buildSnowflakeRuntime());

        NamespaceRuntimeStatusResponse response = idGenerateManager.getRuntimeStatus("order_id");

        assertEquals("order_id", response.getNamespaceCode());
        assertEquals(3, response.getVersion());
        assertEquals(2, response.getIdMode());
        assertEquals("ACTIVE", response.getRuntimeStatus());
    }

    @Test
    void shouldRejectMissingRuntime() {
        assertThrows(BizException.class, () -> idGenerateManager.nextId("missing"));
    }

    @Test
    void shouldRejectInvalidBatchCount() {
        namespaceRegistry.replace("order_id", buildSnowflakeRuntime());

        assertThrows(BizException.class, () -> idGenerateManager.batchNextId("order_id", 0));
        assertThrows(BizException.class, () -> idGenerateManager.batchNextId("order_id", 1001));
    }

    @Test
    void shouldRecordSuccessAndFailureStats() {
        namespaceRegistry.replace("order_id", buildSnowflakeRuntime());

        idGenerateManager.nextId("order_id");
        assertThrows(BizException.class, () -> idGenerateManager.nextId("missing"));

        assertEquals(2, callStatsRecorder.records.size());
        assertEquals(1, callStatsRecorder.records.stream().filter(CallStatsRecord::isSuccess).count());
        assertEquals(1, callStatsRecorder.records.stream().filter(record -> !record.isSuccess()).count());
        assertEquals(1, callStatsRecorder.records.stream().mapToInt(CallStatsRecord::getGeneratedCount).sum());
    }

    private SnowflakeNamespaceRuntime buildSnowflakeRuntime() {
        return new SnowflakeNamespaceRuntime("order_id", 3, 2, new SnowflakeGenerator(1, 1));
    }

    private static class FakeCallStatsRecorder implements CallStatsRecorder {

        private final List<CallStatsRecord> records = new ArrayList<>();

        @Override
        public void record(CallStatsRecord record) {
            records.add(record);
        }

        @Override
        public List<IdCallStatsMinuteDO> drain() {
            return List.of();
        }

        @Override
        public void restore(List<IdCallStatsMinuteDO> records) {
        }
    }
}
