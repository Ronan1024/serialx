package com.ronan.serialx.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * Namespace 持久化对象。
 */
@Getter
@Setter
@TableName("sx_namespace")
public class NamespaceDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Namespace 编码。
     */
    @TableField("namespace_code")
    private String namespaceCode;

    /**
     * Namespace 名称。
     */
    @TableField("namespace_name")
    private String namespaceName;

    /**
     * 发号模式，对应 NamespaceIdModeEnum。
     */
    @TableField("id_mode")
    private Integer idMode;

    /**
     * Namespace 状态，对应 NamespaceStatusEnum。
     */
    private Integer status;

    /**
     * 负责人。
     */
    private String owner;

    /**
     * 备注信息。
     */
    private String remark;

    /**
     * 当前生效版本。
     */
    @TableField("current_version")
    private Integer currentVersion;

    /**
     * 删除标记。
     */
    private Integer deleted;

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
