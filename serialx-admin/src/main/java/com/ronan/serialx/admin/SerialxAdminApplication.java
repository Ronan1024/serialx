package com.ronan.serialx.admin;

import com.ronan.serialx.admin.config.AdminSecurityProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


/**
 * SerialX 后台管理应用启动入口。
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
