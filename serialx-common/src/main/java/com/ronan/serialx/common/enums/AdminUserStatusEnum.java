package com.ronan.serialx.common.enums;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 后台用户状态枚举。
 */
@Getter
@RequiredArgsConstructor
public enum AdminUserStatusEnum {

    /**
     * 禁用状态。
     */
    DISABLED(0, "禁用"),

    /**
     * 启用状态。
     */
    ENABLED(1, "启用");

    /**
     * 状态码。
     */
    private final Integer code;

    /**
     * 状态描述。
     */
    private final String description;

    /**
     * 判断状态码是否为启用状态。
     */
    public static boolean isEnabled(Integer code) {
        return ENABLED.code.equals(code);
    }

    /**
     * 判断状态码是否为已定义状态。
     */
    public static boolean contains(Integer code) {
        return Arrays.stream(values()).anyMatch(status -> status.code.equals(code));
    }
}
