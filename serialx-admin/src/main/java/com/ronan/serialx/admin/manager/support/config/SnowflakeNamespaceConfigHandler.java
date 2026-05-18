package com.ronan.serialx.admin.manager.support.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ronan.serialx.common.enums.NamespaceIdModeEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Snowflake 模式配置处理器。
 */
@Component
@RequiredArgsConstructor
public class SnowflakeNamespaceConfigHandler implements NamespaceConfigHandler<SnowflakeNamespaceConfig> {

    private final ObjectMapper objectMapper;

    @Override
    public NamespaceIdModeEnum supportMode() {
        return NamespaceIdModeEnum.SNOWFLAKE;
    }

    @Override
    public SnowflakeNamespaceConfig parse(JsonNode config) {
        if (config == null || config.isNull()) {
            return new SnowflakeNamespaceConfig();
        }
        if (!config.isObject()) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "Namespace 配置必须是 JSON 对象");
        }
        if (config.isEmpty()) {
            return new SnowflakeNamespaceConfig();
        }
        return objectMapper.convertValue(config, SnowflakeNamespaceConfig.class);
    }

    @Override
    public JsonNode toJson(SnowflakeNamespaceConfig config) {
        return JsonNodeFactory.instance.objectNode();
    }
}
