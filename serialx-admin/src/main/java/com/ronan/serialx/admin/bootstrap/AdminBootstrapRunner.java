package com.ronan.serialx.admin.bootstrap;

import com.ronan.serialx.admin.config.AdminSecurityProperties;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.common.enums.AdminUserStatusEnum;
import com.ronan.serialx.infra.entity.AdminUserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 后台管理员引导创建器。
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    /**
     * 后台安全配置属性。
     */
    private final AdminSecurityProperties properties;

    /**
     * 后台用户数据访问服务。
     */
    private final AdminUserService adminUserService;

    /**
     * 密码哈希编码器。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 根据配置创建初始后台管理员。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.getBootstrapUsername())
                || !StringUtils.hasText(properties.getBootstrapPassword())
                || adminUserService.existsByUsername(properties.getBootstrapUsername())) {
            return;
        }
        AdminUserDO user = new AdminUserDO();
        user.setUsername(properties.getBootstrapUsername());
        user.setPasswordHash(passwordEncoder.encode(properties.getBootstrapPassword()));
        user.setDisplayName(properties.getBootstrapUsername());
        user.setStatus(AdminUserStatusEnum.ENABLED.getCode());
        user.setRoles("ADMIN");
        adminUserService.create(user);
    }
}
