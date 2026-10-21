package com.ronan.serialx.domain.factory;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronan.serialx.common.enums.NamespaceIdModeEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.core.snowflake.SnowflakeGenerator;
import com.ronan.serialx.domain.model.NamespaceRuntimeSnapshot;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import com.ronan.serialx.domain.runtime.SegmentNamespaceRuntime;
import com.ronan.serialx.domain.runtime.SnowflakeNamespaceRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Namespace 运行时工厂。
 */
@Component
@RequiredArgsConstructor
public class NamespaceRuntimeFactory {

    private final SnowflakeGenerator snowflakeGenerator;

    /**
     * 创建 Namespace 运行时。
     */
    public NamespaceRuntime create(NamespaceRuntimeSnapshot snapshot) {
        NamespaceIdModeEnum idMode = NamespaceIdModeEnum.fromCode(snapshot.getIdMode());
        if (NamespaceIdModeEnum.SEGMENT == idMode) {
            return createSegmentRuntime(snapshot);
        }
        if (NamespaceIdModeEnum.SNOWFLAKE == idMode) {
            return createSnowflakeRuntime(snapshot);
        }
        throw new IllegalArgumentException("unsupported namespace id mode, idMode=" + snapshot.getIdMode());
    }

    private NamespaceRuntime createSegmentRuntime(NamespaceRuntimeSnapshot snapshot) {
        JsonNode config = snapshot.getConfig();
        long initialValue = readRequiredLong(config, "initialValue", snapshot.getNamespaceCode());
        long maxValue = readLong(config, "maxValue", Long.MAX_VALUE);
        return new SegmentNamespaceRuntime(
                snapshot.getNamespaceCode(),
                snapshot.getVersion(),
                snapshot.getIdMode(),
                initialValue,
                maxValue);
    }

    private NamespaceRuntime createSnowflakeRuntime(NamespaceRuntimeSnapshot snapshot) {
        return new SnowflakeNamespaceRuntime(
                snapshot.getNamespaceCode(),
                snapshot.getVersion(),
                snapshot.getIdMode(),
                snowflakeGenerator);
    }

    private long readRequiredLong(JsonNode config, String fieldName, String namespaceCode) {
        JsonNode value = config == null ? null : config.get(fieldName);
        if (value == null || value.isNull() || !value.canConvertToLong()) {
            throw new BizException(
                    BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(),
                    "Segment 配置缺少有效字段 " + fieldName + ", namespaceCode=" + namespaceCode);
        }
        return value.asLong();
    }

    private long readLong(JsonNode config, String fieldName, long defaultValue) {
        JsonNode value = config == null ? null : config.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.asLong(defaultValue);
    }
}
