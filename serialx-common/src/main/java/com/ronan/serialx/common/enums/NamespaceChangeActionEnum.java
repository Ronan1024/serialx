package com.ronan.serialx.common.enums;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Namespace 变更动作枚举。
 */
@Getter
@RequiredArgsConstructor
public enum NamespaceChangeActionEnum {

    /**
     * 创建 Namespace。
     */
    CREATE(1, "CREATE"),

    /**
     * 更新基础信息。
     */
    UPDATE(2, "UPDATE"),

    /**
     * 保存草稿配置。
     */
    SAVE_DRAFT(3, "SAVE_DRAFT"),

    /**
     * 发布配置。
     */
    PUBLISH(4, "PUBLISH"),

    /**
     * 回滚版本。
     */
    ROLLBACK(5, "ROLLBACK"),

    /**
     * 启用 Namespace。
     */
    ENABLE(6, "ENABLE"),

    /**
     * 停用 Namespace。
     */
    DISABLE(7, "DISABLE"),

    /**
     * 删除 Namespace。
     */
    DELETE(8, "DELETE");

    /**
     * 动作编码。
     */
    private final Integer code;

    /**
     * 动作名称。
     */
    private final String name;

    /**
     * 根据编码解析动作。
     */
    public static NamespaceChangeActionEnum fromCode(Integer code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
