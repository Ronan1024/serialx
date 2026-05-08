package com.ronan.serialx.common.enums;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Namespace 状态枚举。
 */
@Getter
@RequiredArgsConstructor
public enum NamespaceStatusEnum {

    /**
     * 草稿状态。
     */
    DRAFT(0, "DRAFT"),

    /**
     * 已启用状态。
     */
    ENABLED(1, "ENABLED"),

    /**
     * 已停用状态。
     */
    DISABLED(2, "DISABLED");

    /**
     * 状态编码。
     */
    private final Integer code;

    /**
     * 状态名称。
     */
    private final String name;

    /**
     * 根据编码解析状态。
     */
    public static NamespaceStatusEnum fromCode(Integer code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断状态编码是否存在。
     */
    public static boolean contains(Integer code) {
        return fromCode(code) != null;
    }
}
