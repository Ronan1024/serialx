package com.ronan.serialx.admin.manager;

import java.time.LocalDateTime;

import com.ronan.serialx.admin.config.AdminSecurityProperties;
import com.ronan.serialx.admin.dto.AdminUserResponse;
import com.ronan.serialx.admin.dto.LoginRequest;
import com.ronan.serialx.admin.dto.LoginResponse;
import com.ronan.serialx.admin.security.AdminUserPrincipal;
import com.ronan.serialx.admin.security.JwtTokenManager;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.common.enums.AdminUserStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.infra.entity.AdminUserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 后台认证业务编排。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthManager {

    /**
     * 后台用户数据访问服务。
     */
    private final AdminUserService adminUserService;

    /**
     * 后台用户业务编排。
     */
    private final AdminUserManager adminUserManager;

    /**
     * 密码哈希编码器。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * JWT 管理组件。
     */
    private final JwtTokenManager jwtTokenManager;

    /**
     * 后台安全配置属性。
     */
    private final AdminSecurityProperties securityProperties;

    /**
     * 使用用户名和密码登录
     */
    public LoginResponse login(LoginRequest request) {
        AdminUserDO user = adminUserService.getByUsername(request.getUsername());
        Assert.notNull(user, UnauthorizedException.supplier(BusinessErrorCode.ADMIN_LOGIN_FAILED));
        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        Assert.isTrue(matches, UnauthorizedException.supplier(BusinessErrorCode.ADMIN_LOGIN_FAILED));
        Assert.isTrue(AdminUserStatusEnum.isEnabled(user.getStatus()), ForbiddenException.supplier(BusinessErrorCode.ADMIN_USER_DISABLED));

        adminUserService.updateLastLoginAt(user.getId());
        AdminUserPrincipal principal = new AdminUserPrincipal(user.getId(), user.getUsername(), user.getRoles());
        AdminUserResponse responseUser = adminUserManager.toResponse(adminUserService.getById(user.getId()));
        return LoginResponse.builder()
                .accessToken(jwtTokenManager.issueToken(principal))
                .tokenType("Bearer")
                .expiresInSeconds(securityProperties.getAccessTokenTtl().toSeconds())
                .user(responseUser)
                .loginAt(LocalDateTime.now())
                .build();
    }

    /**
     * 查询当前登录后台用户。
     */
    public AdminUserResponse currentUser(AdminUserPrincipal principal) {

        Assert.notNull(principal, UnauthorizedException.supplier(BusinessErrorCode.ADMIN_UNAUTHORIZED));
        return adminUserManager.getUser(principal.getUserId());
    }
}
