package com.ronan.serialx.admin;

import com.ronan.serialx.admin.config.AdminSecurityProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


/**
 * SerialX 组合控制面启动入口，同时装配后台管理与 coordinator 协调能力。
 * 协调器是库模块，不启动第二个必须部署的应用进程；HTTP 与 gRPC 共用同一权威主库。
 */
@MapperScan("com.ronan.serialx.infra.mapper")
@SpringBootApplication(scanBasePackages = "com.ronan.serialx")
@EnableConfigurationProperties(AdminSecurityProperties.class)
public class SerialxAdminApplication {

    /**
     * 启动后台管理应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(SerialxAdminApplication.class, args);
    }
}
