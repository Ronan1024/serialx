package com.ronan.serialx.common.enums;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Namespace 发号模式枚举。
 */
@Getter
@RequiredArgsConstructor
public enum NamespaceIdModeEnum {

    /**
     * 基于号段模式发号。
     */
    SEGMENT(1, "SEGMENT"),

    /**
     * 基于雪花算法发号。
     */
    SNOWFLAKE(2, "SNOWFLAKE");

    /**
     * 模式编码。
     */
    private final Integer code;

    /**
     * 模式名称。
     */
    private final String name;

    /**
     * 根据编码解析模式。
     */
    public static NamespaceIdModeEnum fromCode(Integer code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断编码是否存在。
     */
    public static boolean contains(Integer code) {
        return fromCode(code) != null;
    }
}
