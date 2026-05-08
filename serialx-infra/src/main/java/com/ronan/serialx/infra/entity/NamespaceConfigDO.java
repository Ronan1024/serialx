package com.ronan.serialx.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * Namespace 版本配置持久化对象。
 */
@Getter
@Setter
@TableName("sx_namespace_config")
public class NamespaceConfigDO {

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
     * 发号模式，对应 NamespaceIdModeEnum。
     */
    @TableField("id_mode")
    private Integer idMode;

    /**
     * 配置内容 JSON。
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 配置校验码。
     */
    @TableField("config_checksum")
    private String configChecksum;

    /**
     * 发布状态，对应 NamespacePublishStatusEnum。
     */
    @TableField("publish_status")
    private Integer publishStatus;

    /**
     * 发布人。
     */
    @TableField("published_by")
    private String publishedBy;

    /**
     * 发布时间。
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
