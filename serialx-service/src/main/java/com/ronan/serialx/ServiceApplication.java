package com.ronan.serialx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Service 应用启动入口。
 */
@MapperScan("com.ronan.serialx.infra.mapper")
@EnableScheduling
@SpringBootApplication
public class ServiceApplication {

    /**
     * 启动 Service 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }
}
