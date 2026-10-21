package com.ronan.serialx.admin.manager;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.admin.convert.AdminUserConvert;
import com.ronan.serialx.admin.dto.AdminUserCreateRequest;
import com.ronan.serialx.admin.dto.AdminUserPasswordRequest;
import com.ronan.serialx.admin.dto.AdminUserResponse;
import com.ronan.serialx.admin.dto.AdminUserUpdateRequest;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.common.enums.AdminUserRoleEnum;
import com.ronan.serialx.common.enums.AdminUserStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.response.PageResult;
import com.ronan.serialx.common.util.Assert;
import com.ronan.serialx.infra.entity.AdminUserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 后台用户业务编排。
 */
@Component
@RequiredArgsConstructor
public class AdminUserManager {

    /**
     * 后台用户数据访问服务。
     */
    private final AdminUserService adminUserService;

    /**
     * 密码哈希编码器。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询后台用户。
     */
    public PageResult<AdminUserResponse> pageUsers(long pageNo, long pageSize, String keyword, Integer status) {
        long normalizedPageNo = Math.clamp(pageNo, 1, Long.MAX_VALUE);
        long normalizedPageSize = Math.clamp(pageSize, 1, 100);
        Page<AdminUserDO> page = adminUserService.pageUsers(normalizedPageNo, normalizedPageSize, keyword, status);
        List<AdminUserResponse> records = page.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 查询后台用户详情。
     */
    public AdminUserResponse getUser(Long id) {
        return toResponse(requireUser(id));
    }

    /**
     * 创建后台用户。
     */
    public AdminUserResponse createUser(AdminUserCreateRequest request) {
        String role = normalizeRole(request.getRoles());
        validateAssignableRole(role);
        if (adminUserService.existsByUsername(request.getUsername())) {
            throw new BizException(BusinessErrorCode.ADMIN_USERNAME_EXISTS);
        }
        AdminUserDO user = new AdminUserDO();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(normalizeDisplayName(request.getDisplayName(), request.getUsername()));
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setStatus(AdminUserStatusEnum.ENABLED.getCode());
        user.setRoles(role);
        return toResponse(adminUserService.create(user));
    }

    /**
     * 更新后台用户基础信息。
     */
    public AdminUserResponse updateUser(Long id, AdminUserUpdateRequest request) {
        AdminUserDO user = requireUser(id);
        validateStatus(request.getStatus());
        boolean systemAdmin = isSystemAdmin(user);
        validateSystemAdminUpdate(user, request);

        user.setDisplayName(normalizeDisplayName(request.getDisplayName(), user.getUsername()));
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        if (!systemAdmin) {
            user.setStatus(request.getStatus() == null ? user.getStatus() : request.getStatus());
            if (StringUtils.hasText(request.getRoles())) {
                String role = normalizeRole(request.getRoles());
                validateAssignableRole(role);
                user.setRoles(role);
            }
        }
        adminUserService.update(user);
        return toResponse(requireUser(id));
    }

    /**
     * 更新后台用户密码。
     */
    public void updatePassword(Long id, AdminUserPasswordRequest request) {
        requireUser(id);
        adminUserService.updatePassword(id, passwordEncoder.encode(request.getPassword()));
    }

    /**
     * 删除后台用户。
     */
    public void deleteUser(Long id) {
        AdminUserDO user = requireUser(id);
        if (isSystemAdmin(user)) {
            throw new BizException(BusinessErrorCode.ADMIN_FORBIDDEN);
        }
        adminUserService.deleteById(id);
    }

    /**
     * 查询用户，不存在时抛出业务异常。
     */
    public AdminUserDO requireUser(Long id) {
        AdminUserDO user = adminUserService.getById(id);
        Assert.notNull(user, BizException.supplier(BusinessErrorCode.ADMIN_USER_NOT_FOUND));
        return user;
    }

    /**
     * 校验后台用户状态码。
     */
    private void validateStatus(Integer status) {
        Assert.isFalse(status != null && !AdminUserStatusEnum.contains(status), BusinessErrorCode.ADMIN_PARAM_INVALID);
    }

    /**
     * 禁止修改系统管理员的角色和状态。
     */
    private void validateSystemAdminUpdate(AdminUserDO user, AdminUserUpdateRequest request) {
        if (!isSystemAdmin(user)) {
            return;
        }
        boolean statusChanged = request.getStatus() != null && !request.getStatus().equals(user.getStatus());
        boolean rolesChanged = StringUtils.hasText(request.getRoles())
                && !request.getRoles().trim().equals(user.getRoles());
        if (statusChanged || rolesChanged) {
            throw new BizException(BusinessErrorCode.ADMIN_FORBIDDEN);
        }
    }

    private boolean isSystemAdmin(AdminUserDO user) {
        return AdminUserRoleEnum.ADMIN.isIncludedIn(user.getRoles());
    }

    /**
     * ADMIN 仅允许由系统启动初始化创建。
     */
    private void validateAssignableRole(String role) {
        if (AdminUserRoleEnum.ADMIN.isIncludedIn(role)) {
            throw new BizException(BusinessErrorCode.ADMIN_FORBIDDEN);
        }
        if (!AdminUserRoleEnum.OPERATOR.getCode().equals(role)
                && !AdminUserRoleEnum.VIEWER.getCode().equals(role)) {
            throw new BizException(BusinessErrorCode.ADMIN_PARAM_INVALID);
        }
    }

    /**
     * 规范化展示名称。
     */
    private String normalizeDisplayName(String displayName, String username) {
        return StringUtils.hasText(displayName) ? displayName.trim() : username;
    }

    /**
     * 规范化用户角色。
     */
    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim() : AdminUserRoleEnum.OPERATOR.getCode();
    }

    /**
     * 将持久化对象转换为接口响应对象。
     */
    public AdminUserResponse toResponse(AdminUserDO user) {
        return AdminUserConvert.INSTANCE.toResponse(user);
    }
}
