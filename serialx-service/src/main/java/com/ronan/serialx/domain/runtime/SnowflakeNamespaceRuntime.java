package com.ronan.serialx.domain.runtime;

import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import java.util.Objects;

/**
 * Snowflake Namespace 包装，只保存路由及版本，不持有独立发号序列。
 */
public class SnowflakeNamespaceRuntime implements NamespaceRuntime {
    private final String namespaceCode;
    private final Integer version;
    private final Integer idMode;
    private final SnowflakeGenerator generator;

    public SnowflakeNamespaceRuntime(String namespaceCode, Integer version, Integer idMode, SnowflakeGenerator generator) {
        this.namespaceCode = namespaceCode;
        this.version = version;
        this.idMode = idMode;
        this.generator = Objects.requireNonNull(generator);
    }

    @Override
    public String getNamespaceCode() {
        return namespaceCode;
    }

    @Override
    public Integer getVersion() {
        return version;
    }

    @Override
    public Integer getIdMode() {
        return idMode;
    }

    @Override
    public long nextId() {
        return generator.nextId();
    }

    @Override
    public boolean isAvailable() {
        return generator.isAvailable();
    }
}
