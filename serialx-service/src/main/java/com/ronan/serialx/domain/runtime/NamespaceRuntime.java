package com.ronan.serialx.domain.runtime;

/**
 * Namespace 本地运行时。
 */
public interface NamespaceRuntime {

    /**
     * 获取 Namespace 编码。
     */
    String getNamespaceCode();

    /**
     * 获取已加载版本。
     */
    Integer getVersion();

    /**
     * 获取发号模式。
     */
    Integer getIdMode();

    /** 是否具备实时发号能力；仅加载配置不代表可用。 */
    default boolean isAvailable() { return false; }

    /** 控制面展示状态，区分已加载与实时可发号。 */
    default String getRuntimeStatus() { return isAvailable() ? "ACTIVE" : "LOADED"; }

    /**
     * 生成下一个 ID。
     */
    long nextId();
}
