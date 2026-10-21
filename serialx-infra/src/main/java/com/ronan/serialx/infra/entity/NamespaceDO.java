package com.ronan.serialx.infra.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Namespace 持久化对象。
 */
@Data
@TableName("sx_namespace")
@EqualsAndHashCode(callSuper = true)
public class NamespaceDO extends BaseDO {

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
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;

}
