package com.ronan.serialx.coordinator.application;

import com.ronan.serialx.common.node.NodeRegisterRequest;
import com.ronan.serialx.coordinator.config.ServiceNodeAllocationProperties;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.infra.entity.SnowflakeNodeDO;
import com.ronan.serialx.infra.mapper.SnowflakeNodeMapper;
import com.ronan.serialx.infra.mapper.SnowflakeNodeSessionMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 成功日志必须位于真实 TransactionTemplate 的提交边界之后，无需连接外部 MySQL。 */
@ExtendWith(OutputCaptureExtension.class)
class SnowflakeNodeManagerLoggingTest {
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private SnowflakeNodeManager manager;
    private final NodeRegisterRequest request = new NodeRegisterRequest("11111111-1111-1111-1111-111111111111", "svc-1");

    @BeforeEach void setUp() {
        var nodes = mock(SnowflakeNodeMapper.class);
        var sessions = mock(SnowflakeNodeSessionMapper.class);
        var validator = mock(Validator.class);
        var properties = new ServiceNodeAllocationProperties();
        properties.setLegacyHttpEnabled(false);
        var node = new SnowflakeNodeDO();
        node.setDatacenterId(1);
        node.setWorkerId(2);
        when(nodes.lockPool()).thenReturn(node);
        when(nodes.lockAvailable(anyLong())).thenReturn(node);
        when(nodes.currentTimeMillis()).thenReturn(SnowflakeGenerator.EPOCH + 1_000);
        when(nodes.assign(any())).thenReturn(1);
        when(sessions.insert(any())).thenReturn(1);
        when(validator.validate(any())).thenReturn(Set.of());
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        manager = new SnowflakeNodeManager(nodes, sessions, properties, transactions, validator);
    }

    @Test void reportsRegistrationOnlyAfterTransactionCommit(CapturedOutput output) {
        doAnswer(invocation -> {
            assertFalse(output.getAll().contains("协调节点注册已提交"));
            return null;
        }).when(transactions).commit(any());
        manager.register(request);
        assertTrue(output.getAll().contains("协调节点注册已提交"));
        assertTrue(output.getAll().contains("runtimeId=" + request.getRuntimeId()));
        assertTrue(output.getAll().contains("datacenterId=1, workerId=2"));
        verify(transactions).commit(any());
    }

    @Test void failedCommitDoesNotReportSuccess(CapturedOutput output) {
        doThrow(new IllegalStateException("secret-transaction-details")).when(transactions).commit(any());
        assertThrows(IllegalStateException.class, () -> manager.register(request));
        assertFalse(output.getAll().contains("协调节点注册已提交"));
        assertFalse(output.getAll().contains("secret-transaction-details"));
    }
}
