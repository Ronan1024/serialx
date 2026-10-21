package com.ronan.serialx.common.enums;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 后台用户角色枚举。
 */
@Getter
@RequiredArgsConstructor
public enum AdminUserRoleEnum {

    /**
     * 管理员。
     */
    ADMIN("ADMIN", "管理员"),

    /**
     * 运维人员。
     */
    OPERATOR("OPERATOR", "运维"),

    /**
     * 只读用户。
     */
    VIEWER("VIEWER", "只读");

    /**
     * 角色编码。
     */
    private final String code;

    /**
     * 角色描述。
     */
    private final String description;

    /**
     * 判断角色列表是否包含当前角色。
     */
    public boolean isIncludedIn(String roles) {
        return roles != null && Arrays.stream(roles.split(","))
                .map(String::trim)
                .anyMatch(code::equals);
    }
}
