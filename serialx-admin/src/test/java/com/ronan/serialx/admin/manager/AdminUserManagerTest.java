package com.ronan.serialx.admin.manager;

import com.ronan.serialx.admin.dto.AdminUserCreateRequest;
import com.ronan.serialx.admin.dto.AdminUserUpdateRequest;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.common.enums.AdminUserRoleEnum;
import com.ronan.serialx.common.enums.AdminUserStatusEnum;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.infra.entity.AdminUserDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManagerTest {

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminUserManager adminUserManager;

    @BeforeEach
    void setUp() {
        adminUserManager = new AdminUserManager(adminUserService, passwordEncoder);
    }

    @Test
    void shouldRejectStatusChangeForSystemAdmin() {
        when(adminUserService.getById(1L)).thenReturn(systemAdmin());
        AdminUserUpdateRequest request = updateRequest();
        request.setStatus(AdminUserStatusEnum.DISABLED.getCode());

        assertThrows(BizException.class, () -> adminUserManager.updateUser(1L, request));

        verify(adminUserService, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectRoleChangeForSystemAdmin() {
        when(adminUserService.getById(1L)).thenReturn(systemAdmin());
        AdminUserUpdateRequest request = updateRequest();
        request.setRoles(AdminUserRoleEnum.OPERATOR.getCode());

        assertThrows(BizException.class, () -> adminUserManager.updateUser(1L, request));

        verify(adminUserService, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldProtectAdminRoleRegardlessOfUsername() {
        AdminUserDO user = systemAdmin();
        user.setUsername("root");
        when(adminUserService.getById(1L)).thenReturn(user);
        AdminUserUpdateRequest request = updateRequest();
        request.setStatus(AdminUserStatusEnum.DISABLED.getCode());

        assertForbidden(() -> adminUserManager.updateUser(1L, request));

        verify(adminUserService, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectAdminRoleWhenCreatingUser() {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername("operator");
        request.setPassword("password123");
        request.setRoles(AdminUserRoleEnum.ADMIN.getCode());

        assertForbidden(() -> adminUserManager.createUser(request));

        verify(adminUserService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldCreateOperatorUser() {
        AdminUserCreateRequest request = createRequest();
        when(adminUserService.existsByUsername("operator")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(adminUserService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(AdminUserRoleEnum.OPERATOR.getCode(), adminUserManager.createUser(request).getRoles());

        verify(adminUserService).create(any());
    }

    @Test
    void shouldRejectDuplicateUsername() {
        AdminUserCreateRequest request = createRequest();
        when(adminUserService.existsByUsername("operator")).thenReturn(true);

        assertErrorCode("B10010005", () -> adminUserManager.createUser(request));

        verify(adminUserService, never()).create(any());
    }

    @Test
    void shouldRejectPromotingUserToAdmin() {
        AdminUserDO user = ordinaryUser();
        when(adminUserService.getById(2L)).thenReturn(user);
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setStatus(AdminUserStatusEnum.ENABLED.getCode());
        request.setRoles(AdminUserRoleEnum.ADMIN.getCode());

        assertForbidden(() -> adminUserManager.updateUser(2L, request));

        verify(adminUserService, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectDeletingAdmin() {
        when(adminUserService.getById(1L)).thenReturn(systemAdmin());

        assertForbidden(() -> adminUserManager.deleteUser(1L));

        verify(adminUserService, never()).deleteById(1L);
    }

    @Test
    void shouldAllowUpdatingUserWithoutChangingStatus() {
        when(adminUserService.getById(2L)).thenReturn(ordinaryUser());
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setDisplayName("新名称");

        assertEquals(AdminUserStatusEnum.ENABLED.getCode(), adminUserManager.updateUser(2L, request).getStatus());
        verify(adminUserService).update(any());
    }

    @Test
    void shouldRejectInvalidUserStatus() {
        when(adminUserService.getById(2L)).thenReturn(ordinaryUser());
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setStatus(99);

        assertErrorCode(com.ronan.serialx.common.error.BusinessErrorCode.ADMIN_PARAM_INVALID.getCode(),
                () -> adminUserManager.updateUser(2L, request));
        verify(adminUserService, never()).update(any());
    }

    @Test
    void shouldRejectMissingUser() {
        assertErrorCode(com.ronan.serialx.common.error.BusinessErrorCode.ADMIN_USER_NOT_FOUND.getCode(),
                () -> adminUserManager.getUser(99L));
    }

    private AdminUserDO systemAdmin() {
        AdminUserDO user = new AdminUserDO();
        user.setId(1L);
        user.setUsername("admin");
        user.setDisplayName("平台管理员");
        user.setStatus(AdminUserStatusEnum.ENABLED.getCode());
        user.setRoles(AdminUserRoleEnum.ADMIN.getCode());
        return user;
    }

    private AdminUserDO ordinaryUser() {
        AdminUserDO user = new AdminUserDO();
        user.setId(2L);
        user.setUsername("operator");
        user.setDisplayName("运维人员");
        user.setStatus(AdminUserStatusEnum.ENABLED.getCode());
        user.setRoles(AdminUserRoleEnum.OPERATOR.getCode());
        return user;
    }

    private AdminUserCreateRequest createRequest() {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername("operator");
        request.setPassword("password123");
        request.setRoles(AdminUserRoleEnum.OPERATOR.getCode());
        return request;
    }

    private void assertForbidden(org.junit.jupiter.api.function.Executable executable) {
        assertErrorCode("B10010003", executable);
    }

    private void assertErrorCode(String expectedCode, org.junit.jupiter.api.function.Executable executable) {
        BizException exception = assertThrows(BizException.class, executable);
        assertEquals(expectedCode, exception.getCode());
    }

    private AdminUserUpdateRequest updateRequest() {
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setDisplayName("平台管理员");
        request.setStatus(AdminUserStatusEnum.ENABLED.getCode());
        request.setRoles(AdminUserRoleEnum.ADMIN.getCode());
        return request;
    }
}
