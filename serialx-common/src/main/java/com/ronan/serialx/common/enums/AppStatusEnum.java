package com.ronan.serialx.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 业务调用方应用状态枚举。
 */
@Getter
@RequiredArgsConstructor
public enum AppStatusEnum {

    /**
     * 禁用。
     */
    DISABLED(0, "禁用"),

    /**
     * 启用。
     */
    ENABLED(1, "启用");

    /**
     * 状态码。
     */
    private final Integer code;

    /**
     * 状态名称。
     */
    private final String name;

    /**
     * 判断状态码是否合法。
     */
    public static boolean contains(Integer code) {
        if (code == null) {
            return false;
        }
        for (AppStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
