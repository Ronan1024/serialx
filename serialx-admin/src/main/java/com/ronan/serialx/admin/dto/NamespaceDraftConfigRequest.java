package com.ronan.serialx.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    @NotNull(message = "发号模式不能为空")
    private Integer idMode;

    /**
     * 草稿配置。
     */
    @JsonAlias("draftConfig")
    private JsonNode config;
}
