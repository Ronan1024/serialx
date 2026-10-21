package com.ronan.serialx.standalone.config;

import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.ronan.serialx.adapter.redis.ServiceInstanceStatusWriterImpl;
import com.ronan.serialx.application.node.SnowflakeNodeLifecycle;
import com.ronan.serialx.common.node.NodeAllocationGateway;
import com.ronan.serialx.standalone.SerialxApplication;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 仅使用本机专用测试实例；新建临时数据库验证 Flyway → 注册 → 加载 → 就绪的真实启动链。 */
@EnabledIfEnvironmentVariable(named = "SERIALX_NODE_TEST_MYSQL_URL", matches = "jdbc:mysql:.*serialx_node_test.*")
@ActiveProfiles("standalone")
@SpringBootTest(classes = SerialxApplication.class, properties = {
        "serialx.admin.node-allocation.machine-token=test-machine-token-with-at-least-32-bytes",
        "serialx.service.snowflake.machine-token=test-machine-token-with-at-least-32-bytes",
        "serialx.admin.security.jwt-secret=test-jwt-key-independent-of-machine-token-32-bytes",
        "serialx.admin.security.bootstrap-username=startup-test-admin",
        "serialx.admin.security.bootstrap-password=test-only-password",
        "serialx.service.instance.instance-id=startup-test-service",
        "serialx.service.instance.advertised-ip=127.0.0.1",
        "serialx.service.instance.enabled=false",
        "serialx.service.instance.namespace-refresh-enabled=false",
        "serialx.service.call-stats.enabled=false"
})
class StandaloneNodeStartupMysqlTest {
    private static final String DATABASE = "serialx_startup_test_" + UUID.randomUUID().toString().replace("-", "");
    private static String baseUrl;
    @Autowired SnowflakeNodeLifecycle lifecycle;
    @Autowired NodeAllocationGateway gateway;
    @Autowired SnowflakeNodeManager manager;
    @Autowired ServiceInstanceIdentity identity;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ServiceInstanceStatusWriterImpl writer;
    @MockitoBean(name = "namespaceRedisMessageListenerContainer") RedisMessageListenerContainer listener;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        baseUrl = System.getenv("SERIALX_NODE_TEST_MYSQL_URL");
        URI address = URI.create(baseUrl.substring("jdbc:".length()));
        assertEquals("/serialx_node_test", address.getPath());
        assertTrue(Set.of("127.0.0.1", "localhost").contains(address.getHost()));
        try (var connection = DriverManager.getConnection(baseUrl, user(), password()); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        registry.add("spring.datasource.url", () -> baseUrl.replace("/serialx_node_test", "/" + DATABASE));
        registry.add("spring.datasource.username", StandaloneNodeStartupMysqlTest::user);
        registry.add("spring.datasource.password", StandaloneNodeStartupMysqlTest::password);
    }

    @Test void migratesBeforeRegistrationAndReleasesAfterClosingGeneration() {
        assertSame(manager, gateway);
        assertTrue(lifecycle.isAvailable());
        assertTrue(lifecycle.generator().nextId() > 0);
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL", Integer.class));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM sx_snowflake_node_session WHERE runtime_id=?", String.class, identity.getRuntimeId()));
        long water = jdbc.queryForObject("SELECT reserved_until_ms FROM sx_snowflake_node_session WHERE runtime_id=?", Long.class, identity.getRuntimeId());
        lifecycle.close();
        assertFalse(lifecycle.isAvailable());
        assertThrows(RuntimeException.class, () -> lifecycle.generator().nextId());
        assertEquals("RELEASED", jdbc.queryForObject("SELECT status FROM sx_snowflake_node_session WHERE runtime_id=?", String.class, identity.getRuntimeId()));
        assertEquals(water, jdbc.queryForObject("SELECT MAX(reserved_until_ms) FROM sx_snowflake_node", Long.class));
    }

    @AfterAll static void removeOwnedDatabase() throws Exception {
        if (baseUrl == null) return;
        try (var connection = DriverManager.getConnection(baseUrl, user(), password()); var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE " + DATABASE);
        }
    }
    private static String user() { return System.getenv().getOrDefault("SERIALX_NODE_TEST_MYSQL_USER", "root"); }
    private static String password() { return System.getenv().getOrDefault("SERIALX_NODE_TEST_MYSQL_PASSWORD", ""); }
}
