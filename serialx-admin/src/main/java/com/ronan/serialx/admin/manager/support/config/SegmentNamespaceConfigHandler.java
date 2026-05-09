package com.ronan.serialx.admin.manager.support.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.enums.NamespaceIdModeEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Segment 模式配置处理器。
 */
@Component
@RequiredArgsConstructor
public class SegmentNamespaceConfigHandler implements NamespaceConfigHandler<SegmentNamespaceConfig> {

    private final ObjectMapper objectMapper;

    @Override
    public NamespaceIdModeEnum supportMode() {
        return NamespaceIdModeEnum.SEGMENT;
    }

    @Override
    public SegmentNamespaceConfig parse(JsonNode config) {
        JsonNode normalizedConfig = requireObject(config, "namespace config is required");
        return objectMapper.convertValue(normalizedConfig, SegmentNamespaceConfig.class);
    }

    @Override
    public JsonNode toJson(SegmentNamespaceConfig config) {
        return objectMapper.valueToTree(config);
    }

    private JsonNode requireObject(JsonNode config, String emptyMessage) {
        if (config == null || config.isNull()) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), emptyMessage);
        }
        if (!config.isObject()) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "namespace config must be json object");
        }
        return config;
    }
}
