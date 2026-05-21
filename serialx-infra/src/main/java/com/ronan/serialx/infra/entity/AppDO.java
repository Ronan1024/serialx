package com.ronan.serialx.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 业务调用方应用持久化对象。
 */
@Getter
@Setter
@TableName("sx_app")
public class AppDO extends BaseDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务调用方 AppKey。
     */
    @TableField("app_key")
    private String appKey;

    /**
     * 应用名称。
     */
    @TableField("app_name")
    private String appName;

    /**
     * 应用状态，对应 AppStatusEnum。
     */
    private Integer status;

    /**
     * 负责人。
     */
    private String owner;

    /**
     * 备注说明。
     */
    private String remark;

}
