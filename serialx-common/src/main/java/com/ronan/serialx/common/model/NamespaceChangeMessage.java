package com.ronan.serialx.common.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Namespace 配置变更通知消息。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceChangeMessage {

    /**
     * 通知频道。
     */
    public static final String DEFAULT_CHANNEL = "serialx:namespace:change";

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * 目标版本号。
     */
    private Integer version;

    /**
     * 事件类型。
     */
    private String eventType;

    /**
     * 发布时间。
     */
    private LocalDateTime publishTime;
}
