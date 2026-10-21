package com.ronan.serialx.standalone;

import com.ronan.serialx.admin.config.AdminSecurityProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SerialX 单进程启动入口。
 *
 * <p>该入口在同一个 JVM 中同时装配 admin 控制面和 service 发号能力。需要横向扩容发号能力时，
 * 仍可额外启动 serialx-service 纯数据面节点。
 */
@MapperScan("com.ronan.serialx.infra.mapper")
@EnableScheduling
@EnableConfigurationProperties(AdminSecurityProperties.class)
@SpringBootApplication(scanBasePackages = {
        "com.ronan.serialx.admin",
        "com.ronan.serialx.coordinator",
        "com.ronan.serialx.standalone.config",
        "com.ronan.serialx.api",
        "com.ronan.serialx.application",
        "com.ronan.serialx.adapter",
        "com.ronan.serialx.config",
        "com.ronan.serialx.domain",
        "com.ronan.serialx.infra",
        "com.ronan.serialx.subscriber",
        "com.ronan.serialx.support"
})
public class SerialxApplication {

    /**
     * 启动单进程应用。
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SerialxApplication.class);
        application.setAdditionalProfiles("standalone");
        application.run(args);
    }
}
