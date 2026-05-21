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

    /**
     * 生成下一个 ID。
     */
    long nextId();
}
