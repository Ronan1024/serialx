package com.ronan.serialx.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存 Namespace 草稿配置请求。
 */
@Getter
@Setter
public class NamespaceDraftConfigRequest {

    /**
     * 发号模式。
     */
    @NotNull
    private Integer idMode;

    /**
     * 草稿配置。
     */
    private JsonNode config;
}
