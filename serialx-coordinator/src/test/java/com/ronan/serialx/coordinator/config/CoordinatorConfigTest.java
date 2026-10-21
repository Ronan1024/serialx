package com.ronan.serialx.coordinator.config;

import com.ronan.serialx.coordinator.persistence.CoordinatorPersistence;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.Validation;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/** 装配和关闭只构建 MyBatis 工厂，不获取数据库连接，不依赖外部 MySQL。 */
class CoordinatorConfigTest {
    @Test
    void shouldKeepPoolPrivateAndCloseItWithContext() {
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var context = new AnnotationConfigApplicationContext();
            HikariDataSource pool;
            try {
                var database = new DataSourceProperties();
                database.setUrl("jdbc:mysql://127.0.0.1:1/serialx_node_test");
                database.setDriverClassName("com.mysql.cj.jdbc.Driver");
                var allocation = new ServiceNodeAllocationProperties();
                allocation.setLegacyHttpEnabled(false);
                context.registerBean(DataSourceProperties.class, () -> database);
                context.registerBean(CoordinatorPersistenceProperties.class, CoordinatorPersistenceProperties::new);
                context.registerBean(ServiceNodeAllocationProperties.class, () -> allocation);
                context.registerBean(jakarta.validation.Validator.class, validators::getValidator);
                context.register(CoordinatorConfig.class);
                context.refresh();
                assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
                assertTrue(context.getBeansOfType(SqlSessionFactory.class).isEmpty());
                assertTrue(context.getBeansOfType(PlatformTransactionManager.class).isEmpty());
                pool = (HikariDataSource) ReflectionTestUtils.getField(
                        context.getBean(CoordinatorPersistence.class), "source");
                assertNotNull(pool);
                assertFalse(pool.isClosed());
                assertEquals(8, pool.getMaximumPoolSize());
                assertEquals(1000, pool.getConnectionTimeout());
            } finally {
                context.close();
            }
            assertTrue(pool.isClosed(), "Spring 销毁 Bean 时必须关闭内部专用池");
        }
    }
}
