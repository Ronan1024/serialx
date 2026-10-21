package com.ronan.serialx.coordinator.config;

import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.ronan.serialx.coordinator.persistence.CoordinatorPersistence;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** admin 与 standalone 共用的协调业务装配，不依赖 HTTP 或 gRPC 传输。 */
@Configuration(proxyBeanMethods = false)
public class CoordinatorConfig {
    /** Flyway 完成主库初始化后再创建协调资源，避免启动期间访问尚不存在的表。 */
    @Bean(destroyMethod = "")
    @DependsOnDatabaseInitialization
    public CoordinatorPersistence coordinatorPersistence(DataSourceProperties database,
                                                           CoordinatorPersistenceProperties properties) throws Exception {
        return new CoordinatorPersistence(database, properties);
    }

    /** 所有协议共享同一协调用例；所有权事实始终存于 MySQL 而非本机内存。 */
    @Bean
    public SnowflakeNodeManager snowflakeNodeManager(CoordinatorPersistence persistence,
                                                   ServiceNodeAllocationProperties properties,
                                                   Validator validator) {
        return new SnowflakeNodeManager(persistence.nodes(), persistence.sessions(), properties,
                persistence.transactions(), validator);
    }
}
