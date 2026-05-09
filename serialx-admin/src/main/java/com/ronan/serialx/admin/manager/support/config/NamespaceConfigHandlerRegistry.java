package com.ronan.serialx.admin.manager.support.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronan.serialx.common.enums.NamespaceIdModeEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Namespace 配置处理器注册表。
 */
@Component
public class NamespaceConfigHandlerRegistry {

    private final Map<NamespaceIdModeEnum, NamespaceConfigHandler<? extends NamespaceModeConfig>> handlers;

    public NamespaceConfigHandlerRegistry(List<NamespaceConfigHandler<? extends NamespaceModeConfig>> handlerList) {
        this.handlers = new EnumMap<>(NamespaceIdModeEnum.class);
        handlerList.forEach(handler -> this.handlers.put(handler.supportMode(), handler));
    }

    /**
     * 根据发号模式标准化配置。
     */
    public JsonNode normalize(Integer idMode, JsonNode config) {
        NamespaceIdModeEnum mode = NamespaceIdModeEnum.fromCode(idMode);
        if (mode == null) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "namespace id mode invalid");
        }
        NamespaceConfigHandler<? extends NamespaceModeConfig> handler = handlers.get(mode);
        if (handler == null) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "unsupported namespace id mode");
        }
        return handler.normalize(config);
    }
}
