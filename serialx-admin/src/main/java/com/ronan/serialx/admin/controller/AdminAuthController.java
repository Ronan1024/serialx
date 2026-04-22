package com.ronan.serialx.admin.controller;

import com.ronan.serialx.admin.dto.AdminUserResponse;
import com.ronan.serialx.admin.dto.LoginRequest;
import com.ronan.serialx.admin.dto.LoginResponse;
import com.ronan.serialx.admin.manager.AdminAuthManager;
import com.ronan.serialx.admin.security.AdminUserPrincipal;
import com.ronan.serialx.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台认证接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    /**
     * 后台认证业务编排。
     */
    private final AdminAuthManager adminAuthManager;

    /**
     * 后台用户名密码登录。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(adminAuthManager.login(request));
    }

    /**
     * 查询当前登录用户。
     */
    @GetMapping("/me")
    public ApiResponse<AdminUserResponse> me(@AuthenticationPrincipal AdminUserPrincipal principal) {
        return ApiResponse.success(adminAuthManager.currentUser(principal));
    }
}
