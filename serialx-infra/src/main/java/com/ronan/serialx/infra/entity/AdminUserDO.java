package com.ronan.serialx.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 后台管理用户持久化对象。
 */
@Getter
@Setter
@TableName("admin_user")
public class AdminUserDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名。
     */
    private String username;

    /**
     * BCrypt 加密后的密码哈希。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 用户展示名称。
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 邮箱地址。
     */
    private String email;

    /**
     * 手机号码。
     */
    private String mobile;

    /**
     * 用户状态，对应 AdminUserStatusEnum。
     */
    private Integer status;

    /**
     * 用户角色，多个角色使用英文逗号分隔。
     */
    private String roles;

    /**
     * 最后登录时间。
     */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

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
