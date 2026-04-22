package com.ronan.serialx.admin.manager;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.admin.dto.AdminUserCreateRequest;
import com.ronan.serialx.admin.dto.AdminUserPasswordRequest;
import com.ronan.serialx.admin.dto.AdminUserResponse;
import com.ronan.serialx.admin.dto.AdminUserUpdateRequest;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.common.enums.AdminUserStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.response.PageResult;
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
        validateStatus(status);
        long normalizedPageNo = Math.max(pageNo, 1);
        long normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
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
        if (adminUserService.existsByUsername(request.getUsername())) {
            throw BizException.conflict(
                    BusinessErrorCode.ADMIN_USERNAME_EXISTS, BusinessErrorCode.ADMIN_USERNAME_EXISTS.getMessage());
        }
        AdminUserDO user = new AdminUserDO();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(normalizeDisplayName(request.getDisplayName(), request.getUsername()));
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setStatus(AdminUserStatusEnum.ENABLED.getCode());
        user.setRoles(normalizeRoles(request.getRoles()));
        return toResponse(adminUserService.create(user));
    }

    /**
     * 更新后台用户基础信息。
     */
    public AdminUserResponse updateUser(Long id, AdminUserUpdateRequest request) {
        AdminUserDO user = requireUser(id);
        user.setDisplayName(normalizeDisplayName(request.getDisplayName(), user.getUsername()));
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        validateStatus(request.getStatus());
        user.setStatus(request.getStatus() == null ? user.getStatus() : request.getStatus());
        user.setRoles(normalizeRoles(request.getRoles()));
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
        requireUser(id);
        adminUserService.deleteById(id);
    }

    /**
     * 查询用户，不存在时抛出业务异常。
     */
    private AdminUserDO requireUser(Long id) {
        AdminUserDO user = adminUserService.getById(id);
        if (user == null) {
            throw BizException.notFound(
                    BusinessErrorCode.ADMIN_USER_NOT_FOUND, BusinessErrorCode.ADMIN_USER_NOT_FOUND.getMessage());
        }
        return user;
    }

    /**
     * 校验后台用户状态码。
     */
    private void validateStatus(Integer status) {
        if (status != null && !AdminUserStatusEnum.contains(status)) {
            throw BizException.badRequest(
                    BusinessErrorCode.ADMIN_PARAM_INVALID, BusinessErrorCode.ADMIN_PARAM_INVALID.getMessage());
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
    private String normalizeRoles(String roles) {
        return StringUtils.hasText(roles) ? roles.trim() : "ADMIN";
    }

    /**
     * 将持久化对象转换为接口响应对象。
     */
    public AdminUserResponse toResponse(AdminUserDO user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .status(user.getStatus())
                .roles(user.getRoles())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
