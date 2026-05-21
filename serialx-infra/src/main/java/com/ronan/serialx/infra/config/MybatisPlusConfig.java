package com.ronan.serialx.infra.config;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 基础配置。
 */
@Configuration
public class MybatisPlusConfig {

    private static final String CREATED_AT = "createdAt";

    private static final String UPDATED_AT = "updatedAt";

    private static final String DELETED = "deleted";

    /**
     * 注册 MyBatis-Plus 分页拦截器。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, CREATED_AT, LocalDateTime.class, now);
                this.strictInsertFill(metaObject, UPDATED_AT, LocalDateTime.class, now);
                this.strictInsertFill(metaObject, DELETED, Integer.class, 0);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, UPDATED_AT, LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
