package com.ronan.serialx.common.enums;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Namespace 配置发布状态枚举。
 */
@Getter
@RequiredArgsConstructor
public enum NamespacePublishStatusEnum {

    /**
     * 草稿编辑中。
     */
    EDITING(0, "EDITING"),

    /**
     * 已发布。
     */
    PUBLISHED(1, "PUBLISHED"),

    /**
     * 已被回滚替代。
     */
    ROLLED_BACK(2, "ROLLED_BACK");

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
    public static NamespacePublishStatusEnum fromCode(Integer code) {
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
