package com.ronan.serialx.coordinator.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ronan.serialx.coordinator.config.CoordinatorPersistenceProperties;
import com.ronan.serialx.infra.mapper.SnowflakeNodeMapper;
import com.ronan.serialx.infra.mapper.SnowflakeNodeSessionMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 独立协调连接池和 MyBatis 会话，只装配节点表 Mapper。
 * 资源保存在本对象内部，不注册第二个 DataSource/SqlSessionFactory Bean，避免后台自动配置退让。
 */
public final class CoordinatorPersistence implements AutoCloseable {
    private final HikariDataSource source;
    private final SqlSessionTemplate sessions;
    private final PlatformTransactionManager transactions;

    /** 与后台使用同一权威主库和凭证，但不共享连接配额。 */
    public CoordinatorPersistence(DataSourceProperties database, CoordinatorPersistenceProperties properties)
            throws Exception {
        properties.validate();
        source = database.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        try {
            source.setPoolName("serialx-coordinator");
            source.setMaximumPoolSize(properties.getMaximumPoolSize());
            source.setMinimumIdle(properties.getMinimumIdle());
            source.setConnectionTimeout(properties.getConnectionTimeout().toMillis());
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(SnowflakeNodeMapper.class);
            configuration.addMapper(SnowflakeNodeSessionMapper.class);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            sessions = new SqlSessionTemplate(factory.getObject());
            transactions = new DataSourceTransactionManager(source);
        } catch (Exception | Error failure) {
            source.close();
            throw failure;
        }
    }

    /** 节点池 Mapper 与会话 Mapper 必须使用同一专用事务连接。 */
    public SnowflakeNodeMapper nodes() { return sessions.getMapper(SnowflakeNodeMapper.class); }
    public SnowflakeNodeSessionMapper sessions() { return sessions.getMapper(SnowflakeNodeSessionMapper.class); }
    public PlatformTransactionManager transactions() { return transactions; }

    /** 应用退出时关闭专用池，不触碰主池或主动释放持久化节点租约。 */
    @Override
    @PreDestroy
    public void close() { source.close(); }
}
