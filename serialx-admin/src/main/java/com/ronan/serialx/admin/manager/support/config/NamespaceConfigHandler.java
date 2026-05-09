package com.ronan.serialx.admin.manager.support.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronan.serialx.common.enums.NamespaceIdModeEnum;

/**
 * Namespace 配置处理器。
 */
public interface NamespaceConfigHandler<T extends NamespaceModeConfig> {

    /**
     * 当前处理器支持的发号模式。
     */
    NamespaceIdModeEnum supportMode();

    /**
     * 将请求配置解析为强类型对象。
     */
    T parse(JsonNode config);

    /**
     * 将配置对象标准化为可持久化 JSON。
     */
    JsonNode toJson(T config);

    /**
     * 标准化并校验配置。
     */
    default JsonNode normalize(JsonNode config) {
        T parsedConfig = parse(config);
        parsedConfig.validate();
        return toJson(parsedConfig);
    }
}
