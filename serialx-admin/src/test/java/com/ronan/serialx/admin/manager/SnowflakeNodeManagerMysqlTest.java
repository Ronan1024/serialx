package com.ronan.serialx.admin.manager;

import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ronan.serialx.coordinator.config.ServiceNodeAllocationProperties;
import com.ronan.serialx.coordinator.config.CoordinatorPersistenceProperties;
import com.ronan.serialx.coordinator.persistence.CoordinatorPersistence;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.*;
import com.ronan.serialx.infra.mapper.SnowflakeNodeMapper;
import com.ronan.serialx.infra.mapper.SnowflakeNodeSessionMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 仅连接独立测试库；测试时不读取生产数据库配置。 */
@EnabledIfEnvironmentVariable(named = "SERIALX_NODE_TEST_MYSQL_URL", matches = "jdbc:mysql:.*serialx_node_test.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SnowflakeNodeManagerMysqlTest {
    private JdbcTemplate jdbc;
    private SnowflakeNodeManager manager;

    @BeforeAll
    void connect() throws Exception {
        String url = System.getenv("SERIALX_NODE_TEST_MYSQL_URL");
        var address = java.net.URI.create(url.substring("jdbc:".length()));
        assertEquals("/serialx_node_test", address.getPath(), "只允许使用专用节点测试库");
        assertTrue(Set.of("127.0.0.1", "localhost").contains(address.getHost()), "只允许连接本机隔离测试实例");
        var source = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("SERIALX_NODE_TEST_MYSQL_USER", "root"),
                System.getenv().getOrDefault("SERIALX_NODE_TEST_MYSQL_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        // 测试库允许重复执行，只删除本测试拥有的两张表。
        jdbc.execute("DROP TABLE IF EXISTS sx_snowflake_node_session");
        jdbc.execute("DROP TABLE IF EXISTS sx_snowflake_node");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__create_snowflake_node_allocation.sql"))
                .execute(source);
        var factory = new MybatisSqlSessionFactoryBean();
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SnowflakeNodeMapper.class);
        config.addMapper(SnowflakeNodeSessionMapper.class);
        factory.setConfiguration(config);
        factory.setDataSource(source);
        var sql = new SqlSessionTemplate(factory.getObject());
        var properties = new ServiceNodeAllocationProperties();
        properties.setMachineToken("test-only-machine-token-32-bytes-long");
        manager = new SnowflakeNodeManager(sql.getMapper(SnowflakeNodeMapper.class),
                sql.getMapper(SnowflakeNodeSessionMapper.class), properties,
                new DataSourceTransactionManager(source), Validation.buildDefaultValidatorFactory().getValidator());
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM sx_snowflake_node_session");
        jdbc.update("UPDATE sx_snowflake_node SET owner_runtime_id=NULL, lease_expire_at_ms=0, reserved_until_ms=0");
    }

    @Test
    void shouldDenyAnotherAuthenticatedInstanceWithoutMutatingLease() {
        var request = request();
        NodeLease lease = manager.register(request);
        long watermarkBefore = watermark(lease);
        long expiryBefore = jdbc.queryForObject(
                "SELECT lease_expire_at_ms FROM sx_snowflake_node_session WHERE runtime_id=?", Long.class, lease.getRuntimeId());
        var renewDenied = assertThrows(NodeAllocationException.class,
                () -> manager.renew(identity(lease), "another-instance"));
        assertEquals(403, renewDenied.getHttpStatus());
        assertEquals(BusinessErrorCode.NODE_AUTH_FAILED.getCode(), renewDenied.getCode());
        var releaseDenied = assertThrows(NodeAllocationException.class,
                () -> manager.release(identity(lease), "another-instance"));
        assertEquals(403, releaseDenied.getHttpStatus());
        assertEquals(watermarkBefore, watermark(lease));
        assertEquals(expiryBefore, jdbc.queryForObject(
                "SELECT lease_expire_at_ms FROM sx_snowflake_node_session WHERE runtime_id=?", Long.class, lease.getRuntimeId()));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM sx_snowflake_node_session WHERE runtime_id=?", String.class, lease.getRuntimeId()));
        assertEquals(lease.getRuntimeId(), jdbc.queryForObject(
                "SELECT owner_runtime_id FROM sx_snowflake_node WHERE datacenter_id=? AND worker_id=?",
                String.class, lease.getDatacenterId(), lease.getWorkerId()));
        assertDoesNotThrow(() -> manager.renew(identity(lease), request.getInstanceId()));
        assertDoesNotThrow(() -> manager.release(identity(lease), request.getInstanceId()));
    }

    @Test
    void shouldTransferOperationsAcrossIndependentControlPlanePools() throws Exception {
        var database = new DataSourceProperties();
        database.setUrl(System.getenv("SERIALX_NODE_TEST_MYSQL_URL"));
        database.setUsername(System.getenv().getOrDefault("SERIALX_NODE_TEST_MYSQL_USER", "root"));
        database.setPassword(System.getenv().getOrDefault("SERIALX_NODE_TEST_MYSQL_PASSWORD", ""));
        var properties = new ServiceNodeAllocationProperties();
        properties.setLegacyHttpEnabled(false);
        // 不共享 Mapper、会话工厂、事务管理器或连接池，模拟两个控制面进程。
        try (var first = new CoordinatorPersistence(database, new CoordinatorPersistenceProperties());
             var second = new CoordinatorPersistence(database, new CoordinatorPersistenceProperties());
             var validators = Validation.buildDefaultValidatorFactory()) {
            var a = new SnowflakeNodeManager(first.nodes(), first.sessions(), properties,
                    first.transactions(), validators.getValidator());
            var b = new SnowflakeNodeManager(second.nodes(), second.sessions(), properties,
                    second.transactions(), validators.getValidator());
            var request = request();
            NodeLease registered = a.register(request);
            NodeLease retry = b.register(request);
            assertEquals(registered.getRuntimeId(), retry.getRuntimeId());
            assertEquals(registered.getDatacenterId(), retry.getDatacenterId());
            assertEquals(registered.getWorkerId(), retry.getWorkerId());
            assertEquals(registered.getLeaseExpireAtMs(), retry.getLeaseExpireAtMs());
            NodeLease renewed = b.renew(identity(registered), request.getInstanceId());
            assertEquals(registered.getValidFromMs(), renewed.getValidFromMs());
            assertTrue(renewed.getReservedUntilMs() >= registered.getReservedUntilMs());
            a.release(identity(renewed), request.getInstanceId());
            b.release(identity(renewed), request.getInstanceId());
            assertEquals("RELEASED", jdbc.queryForObject(
                    "SELECT status FROM sx_snowflake_node_session WHERE runtime_id=?", String.class, registered.getRuntimeId()));
            assertEquals(renewed.getReservedUntilMs(), watermark(renewed));
            assertNodeError(BusinessErrorCode.NODE_LEASE_LOST,
                    () -> b.renew(identity(renewed), request.getInstanceId()));
        }
    }

    @Test
    void shouldAllocateDifferentPairsForConcurrentInstances() throws Exception {
        List<NodeLease> leases = parallel(32, () -> manager.register(request()));
        assertEquals(32, leases.stream().map(lease -> lease.getDatacenterId() * 32 + lease.getWorkerId()).distinct().count());
        assertEquals(32, jdbc.queryForObject("SELECT COUNT(*) FROM sx_snowflake_node_session", Integer.class));
        leases.forEach(lease -> {
            assertTrue(lease.getValidFromMs() <= lease.getReservedUntilMs());
            assertTrue(lease.getLeaseDurationMs() > 0);
        });
    }

    @Test
    void shouldReturnSameSessionForConcurrentRegistrationAndResponseLoss() throws Exception {
        var request = request();
        List<NodeLease> leases = parallel(16, () -> manager.register(request));
        assertEquals(1, leases.stream().map(lease -> lease.getDatacenterId() * 32 + lease.getWorkerId()).distinct().count());
        NodeLease original = leases.getFirst();
        NodeLease retry = manager.register(request);
        assertEquals(original.getValidFromMs(), retry.getValidFromMs());
        assertEquals(original.getReservedUntilMs(), retry.getReservedUntilMs());
        assertEquals(original.getLeaseExpireAtMs(), retry.getLeaseExpireAtMs());
        assertTrue(retry.getLeaseDurationMs() <= original.getLeaseDurationMs());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sx_snowflake_node_session", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sx_snowflake_node WHERE owner_runtime_id IS NOT NULL", Integer.class));
    }

    @Test
    void shouldFillEntirePoolAndRejectNextInstance() {
        var ids = new HashSet<Integer>();
        for (int i = 0; i < 1024; i++) {
            NodeLease lease = manager.register(request());
            assertTrue(ids.add(lease.getDatacenterId() * 32 + lease.getWorkerId()));
        }
        assertNodeError(BusinessErrorCode.NODE_POOL_EXHAUSTED, () -> manager.register(request()));
        assertEquals(1024, jdbc.queryForObject("SELECT COUNT(*) FROM sx_snowflake_node_session", Integer.class));
    }

    @Test
    void shouldRenewWithoutReducingWatermarkAndRejectWrongOwner() {
        NodeLease lease = manager.register(request());
        NodeLease renewed = manager.renew(identity(lease));
        assertEquals(lease.getValidFromMs(), renewed.getValidFromMs());
        assertTrue(renewed.getReservedUntilMs() >= lease.getReservedUntilMs());
        assertTrue(renewed.getLeaseExpireAtMs() >= lease.getLeaseExpireAtMs());
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST,
                () -> manager.renew(new NodeIdentityRequest(UUID.randomUUID().toString(), lease.getDatacenterId(), lease.getWorkerId())));
    }

    @Test
    void shouldNotReactivateExpiredOrReleasedSessions() {
        var request = request();
        NodeLease lease = manager.register(request);
        jdbc.update("UPDATE sx_snowflake_node_session SET lease_expire_at_ms=1 WHERE runtime_id=?", lease.getRuntimeId());
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST, () -> manager.renew(identity(lease)));
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST, () -> manager.register(request));
        assertEquals("EXPIRED", jdbc.queryForObject("SELECT status FROM sx_snowflake_node_session WHERE runtime_id=?", String.class, lease.getRuntimeId()));

        var secondRequest = request();
        NodeLease second = manager.register(secondRequest);
        manager.release(identity(second));
        manager.release(identity(second));
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST, () -> manager.register(secondRequest));
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST, () -> manager.renew(identity(second)));
        assertEquals(second.getReservedUntilMs(), watermark(second));
    }

    @Test
    void shouldProtectReplacementFromDelayedRequestsAndKeepTimeRangesSeparate() {
        // 只留下一个安全槽位，强制复用同一组合；历史窗口已经落在当前时间之前。
        jdbc.update("UPDATE sx_snowflake_node SET reserved_until_ms=9223372036854775807 WHERE NOT (datacenter_id=0 AND worker_id=0)");
        long past = jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)", Long.class) - 100;
        var oldRequest = request();
        NodeLease old = new NodeLease();
        old.setRuntimeId(oldRequest.getRuntimeId());
        old.setDatacenterId(0);
        old.setWorkerId(0);
        old.setValidFromMs(past - 1000);
        old.setReservedUntilMs(past);
        old.setLeaseExpireAtMs(past);
        old.setLeaseDurationMs(0);
        old.setEpoch(1704067200000L);
        old.setWorkerIdBits(5);
        old.setDatacenterIdBits(5);
        old.setSequenceBits(12);
        jdbc.update("UPDATE sx_snowflake_node SET owner_runtime_id=?, lease_expire_at_ms=?, reserved_until_ms=? WHERE datacenter_id=0 AND worker_id=0", old.getRuntimeId(), past, past);
        jdbc.update("INSERT INTO sx_snowflake_node_session (runtime_id,instance_id,datacenter_id,worker_id,valid_from_ms,reserved_until_ms,lease_expire_at_ms,status) VALUES (?,?,0,0,?,?,?,'ACTIVE')",
                old.getRuntimeId(), oldRequest.getInstanceId(), old.getValidFromMs(), past, past);
        NodeLease replacement = manager.register(request());
        assertEquals(old.getDatacenterId(), replacement.getDatacenterId());
        assertEquals(old.getWorkerId(), replacement.getWorkerId());
        assertTrue(replacement.getValidFromMs() > past);
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST, () -> manager.renew(identity(old)));
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST, () -> manager.release(identity(old)));
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST, () -> manager.register(oldRequest));
        assertEquals(replacement.getRuntimeId(), jdbc.queryForObject(
                "SELECT owner_runtime_id FROM sx_snowflake_node WHERE datacenter_id=0 AND worker_id=0", String.class));
        assertEquals(replacement.getReservedUntilMs(), watermark(replacement));
    }

    @Test
    void shouldNotReuseReleasedNodeBeforeItsReservedWindowHasPassed() {
        jdbc.update("UPDATE sx_snowflake_node SET reserved_until_ms=9223372036854775807 WHERE NOT (datacenter_id=0 AND worker_id=0)");
        NodeLease lease = manager.register(request());
        manager.release(identity(lease));
        assertNodeError(BusinessErrorCode.NODE_POOL_EXHAUSTED, () -> manager.register(request()));
        assertEquals(lease.getReservedUntilMs(), watermark(lease));
    }

    @Test
    void shouldRollBackAllocationWhenSessionInsertFails() {
        jdbc.execute("CREATE TRIGGER sx_test_reject_session BEFORE INSERT ON sx_snowflake_node_session FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test insert failure'");
        try {
            assertThrows(RuntimeException.class, () -> manager.register(request()));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sx_snowflake_node WHERE owner_runtime_id IS NOT NULL", Integer.class));
            assertEquals(0L, jdbc.queryForObject("SELECT MAX(reserved_until_ms) FROM sx_snowflake_node", Long.class));
        } finally {
            jdbc.execute("DROP TRIGGER sx_test_reject_session");
        }
    }

    @Test
    void shouldRejectInvalidParametersAndRuntimeIdentityReuse() {
        assertNodeError(BusinessErrorCode.NODE_PARAM_INVALID, () -> manager.register(new NodeRegisterRequest("invalid", "svc")));
        var request = request();
        NodeLease lease = manager.register(request);
        assertNodeError(BusinessErrorCode.NODE_LEASE_LOST,
                () -> manager.register(new NodeRegisterRequest(request.getRuntimeId(), "another-instance")));
        assertNodeError(BusinessErrorCode.NODE_PARAM_INVALID,
                () -> manager.renew(new NodeIdentityRequest(lease.getRuntimeId(), 32, 0)));
    }

    @Test
    void shouldApplyAllMigrationsWithFlyway() {
        String database = "serialx_node_test_flyway_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            String url = System.getenv("SERIALX_NODE_TEST_MYSQL_URL").replace("/serialx_node_test", "/" + database);
            var result = org.flywaydb.core.Flyway.configure()
                    .dataSource(url, System.getenv().getOrDefault("SERIALX_NODE_TEST_MYSQL_USER", "root"),
                            System.getenv().getOrDefault("SERIALX_NODE_TEST_MYSQL_PASSWORD", ""))
                    .locations("classpath:db/migration").load().migrate();
            assertEquals(6, result.migrationsExecuted);
        } finally {
            jdbc.execute("DROP DATABASE " + database);
        }
    }

    @Test
    void shouldBoundPoolLockWaitAndLeaveNoAllocation() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeQuery("SELECT * FROM sx_snowflake_node WHERE datacenter_id=0 AND worker_id=0 FOR UPDATE").close();
                try (var executor = Executors.newSingleThreadExecutor()) {
                    var future = executor.submit(() -> manager.register(request()));
                    assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
                }
            } finally {
                connection.rollback();
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sx_snowflake_node_session", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sx_snowflake_node WHERE owner_runtime_id IS NOT NULL", Integer.class));
    }

    private NodeRegisterRequest request() { return new NodeRegisterRequest(UUID.randomUUID().toString(), "test-service"); }
    private NodeIdentityRequest identity(NodeLease lease) {
        return new NodeIdentityRequest(lease.getRuntimeId(), lease.getDatacenterId(), lease.getWorkerId());
    }
    private long watermark(NodeLease lease) {
        return jdbc.queryForObject("SELECT reserved_until_ms FROM sx_snowflake_node WHERE datacenter_id=? AND worker_id=?", Long.class,
                lease.getDatacenterId(), lease.getWorkerId());
    }
    private void assertNodeError(BusinessErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code.getCode(), assertThrows(NodeAllocationException.class, action).getCode());
    }
    private List<NodeLease> parallel(int count, Callable<NodeLease> task) throws Exception {
        try (var executor = Executors.newFixedThreadPool(count)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<NodeLease>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> { start.await(); return task.call(); }));
            start.countDown();
            List<NodeLease> leases = new ArrayList<>();
            for (var future : futures) leases.add(future.get(30, TimeUnit.SECONDS));
            return leases;
        }
    }
}
