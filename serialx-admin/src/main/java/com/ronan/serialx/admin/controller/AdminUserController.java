package com.ronan.serialx.admin.controller;

import com.ronan.serialx.admin.dto.AdminUserCreateRequest;
import com.ronan.serialx.admin.dto.AdminUserPageQueryRequest;
import com.ronan.serialx.admin.dto.AdminUserPasswordRequest;
import com.ronan.serialx.admin.dto.AdminUserResponse;
import com.ronan.serialx.admin.dto.AdminUserUpdateRequest;
import com.ronan.serialx.admin.manager.AdminUserManager;
import com.ronan.serialx.common.response.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {


    /**
     * 后台用户业务编排。
     */
    private final AdminUserManager adminUserManager;

    /**
     * 分页查询后台用户。
     */
    @PostMapping("/page")
    public PageResult<AdminUserResponse> pageUsers(@RequestBody AdminUserPageQueryRequest request) {
        return adminUserManager.pageUsers(
                request.getPageNo(), request.getPageSize(), request.getKeyword(), request.getStatus());
    }

    /**
     * 查询后台用户详情。
     */
    @GetMapping("/{id}")
    public AdminUserResponse getUser(@PathVariable Long id) {
        return adminUserManager.getUser(id);
    }

    /**
     * 创建后台用户。
     */
    @PostMapping
    public AdminUserResponse createUser(@Validated @RequestBody AdminUserCreateRequest request) {
        return adminUserManager.createUser(request);
    }

    /**
     * 更新后台用户基础信息。
     */
    @PutMapping("/{id}")
    public AdminUserResponse updateUser(
            @PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserManager.updateUser(id, request);
    }

    /**
     * 更新后台用户密码。
     */
    @PutMapping("/{id}/password")
    public void updatePassword(
            @PathVariable Long id, @Valid @RequestBody AdminUserPasswordRequest request) {
        adminUserManager.updatePassword(id, request);
    }

    /**
     * 删除后台用户。
     */
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        adminUserManager.deleteUser(id);
    }
}
