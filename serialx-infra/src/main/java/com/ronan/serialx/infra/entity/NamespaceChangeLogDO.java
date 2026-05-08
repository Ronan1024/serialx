package com.ronan.serialx.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * Namespace 变更日志持久化对象。
 */
@Getter
@Setter
@TableName("sx_namespace_change_log")
public class NamespaceChangeLogDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Namespace 主键 ID。
     */
    @TableField("namespace_id")
    private Long namespaceId;

    /**
     * 配置版本号。
     */
    private Integer version;

    /**
     * 变更动作，对应 NamespaceChangeActionEnum。
     */
    @TableField("action_type")
    private Integer actionType;

    /**
     * 变更前内容。
     */
    @TableField("before_json")
    private String beforeJson;

    /**
     * 变更后内容。
     */
    @TableField("after_json")
    private String afterJson;

    /**
     * 操作人。
     */
    private String operator;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
