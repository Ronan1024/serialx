package com.ronan.serialx.admin.manager;

import com.ronan.serialx.admin.config.AdminSecurityProperties;
import com.ronan.serialx.admin.dto.LoginRequest;
import com.ronan.serialx.admin.security.AdminUserPrincipal;
import com.ronan.serialx.admin.security.JwtTokenManager;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.common.enums.AdminUserStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.ForbiddenException;
import com.ronan.serialx.common.exception.UnauthorizedException;
import com.ronan.serialx.infra.entity.AdminUserDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthManagerTest {
    @Mock private AdminUserService users;
    @Mock private AdminUserManager userManager;
    @Mock private PasswordEncoder passwords;
    @Mock private JwtTokenManager tokens;
    private AdminAuthManager manager;
    private LoginRequest request;

    @BeforeEach
    void setUp() {
        manager = new AdminAuthManager(users, userManager, passwords, tokens, new AdminSecurityProperties());
        request = new LoginRequest();
        request.setUsername("operator");
        request.setPassword("password");
    }

    @Test
    void shouldRejectMissingUserWithoutReadingPassword() {
        var ex = assertThrows(UnauthorizedException.class, () -> manager.login(request));
        assertEquals(BusinessErrorCode.ADMIN_LOGIN_FAILED.getCode(), ex.getCode());
        verifyNoInteractions(passwords, tokens);
    }

    @Test
    void shouldRejectWrongPassword() {
        stubUser(AdminUserStatusEnum.ENABLED.getCode(), false);
        assertThrows(UnauthorizedException.class, () -> manager.login(request));
        verify(users, never()).updateLastLoginAt(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldRejectDisabledUser() {
        stubUser(AdminUserStatusEnum.DISABLED.getCode(), true);
        var ex = assertThrows(ForbiddenException.class, () -> manager.login(request));
        assertEquals(BusinessErrorCode.ADMIN_USER_DISABLED.getCode(), ex.getCode());
        verify(users, never()).updateLastLoginAt(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldAllowEnabledUserWithCorrectPassword() {
        stubUser(AdminUserStatusEnum.ENABLED.getCode(), true);
        when(tokens.issueToken(any())).thenReturn("token");
        assertEquals("token", manager.login(request).getAccessToken());
        verify(users).updateLastLoginAt(1L);
    }

    @Test
    void shouldRejectMissingPrincipal() {
        assertThrows(UnauthorizedException.class, () -> manager.currentUser(null));
        verifyNoInteractions(userManager);
    }

    @Test
    void shouldResolveAuthenticatedPrincipal() {
        manager.currentUser(new AdminUserPrincipal(1L, "operator", "OPERATOR"));
        verify(userManager).getUser(1L);
    }

    private void stubUser(Integer status, boolean matches) {
        AdminUserDO user = new AdminUserDO();
        user.setId(1L);
        user.setUsername("operator");
        user.setRoles("OPERATOR");
        user.setStatus(status);
        user.setPasswordHash("hash");
        when(users.getByUsername("operator")).thenReturn(user);
        when(passwords.matches("password", "hash")).thenReturn(matches);
    }
}
