package com.ronan.serialx.admin.controller;

import com.ronan.serialx.admin.dto.AdminUserCreateRequest;
import com.ronan.serialx.admin.dto.AdminUserPasswordRequest;
import com.ronan.serialx.admin.dto.AdminUserResponse;
import com.ronan.serialx.admin.dto.AdminUserUpdateRequest;
import com.ronan.serialx.admin.manager.AdminUserManager;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.common.response.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @GetMapping
    public ApiResponse<PageResult<AdminUserResponse>> pageUsers(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(adminUserManager.pageUsers(pageNo, pageSize, keyword, status));
    }

    /**
     * 查询后台用户详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.success(adminUserManager.getUser(id));
    }

    /**
     * 创建后台用户。
     */
    @PostMapping
    public ApiResponse<AdminUserResponse> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        return ApiResponse.success(adminUserManager.createUser(request));
    }

    /**
     * 更新后台用户基础信息。
     */
    @PutMapping("/{id}")
    public ApiResponse<AdminUserResponse> updateUser(
            @PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return ApiResponse.success(adminUserManager.updateUser(id, request));
    }

    /**
     * 更新后台用户密码。
     */
    @PutMapping("/{id}/password")
    public ApiResponse<Void> updatePassword(
            @PathVariable Long id, @Valid @RequestBody AdminUserPasswordRequest request) {
        adminUserManager.updatePassword(id, request);
        return ApiResponse.success();
    }

    /**
     * 删除后台用户。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        adminUserManager.deleteUser(id);
        return ApiResponse.success();
    }
}
