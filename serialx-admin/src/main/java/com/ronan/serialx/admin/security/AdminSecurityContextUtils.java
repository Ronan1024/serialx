package com.ronan.serialx.admin.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 后台安全上下文工具类。
 */
public final class AdminSecurityContextUtils {

    private static final String DEFAULT_OPERATOR = "system";

    private AdminSecurityContextUtils() {
    }

    /**
     * 获取当前登录用户名，未登录时返回默认操作人。
     */
    public static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminUserPrincipal principal) {
            return principal.getUsername();
        }
        return DEFAULT_OPERATOR;
    }
}
