package com.ronan.serialx.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 业务调用方应用密钥持久化对象。
 */
@Getter
@Setter
@TableName("sx_app_secret")
public class AppSecretDO extends BaseDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 应用主键ID。
     */
    @TableField("app_id")
    private Long appId;

    /**
     * AppSecret 的 SHA-256 摘要。
     */
    @TableField("secret_hash")
    private String secretHash;

    /**
     * 密钥状态，对应 AppStatusEnum。
     */
    private Integer status;

}
