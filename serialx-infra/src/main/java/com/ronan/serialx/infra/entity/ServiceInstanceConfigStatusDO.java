package com.ronan.serialx.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * Service 实例配置状态持久化对象。
 */
@Getter
@Setter
@TableName("sx_service_instance_config_status")
public class ServiceInstanceConfigStatusDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 实例 ID。
     */
    @TableField("instance_id")
    private String instanceId;

    /**
     * 服务名称。
     */
    @TableField("service_name")
    private String serviceName;

    /**
     * 主机标识。
     */
    private String host;

    /**
     * Namespace 编码。
     */
    @TableField("namespace_code")
    private String namespaceCode;

    /**
     * 已加载版本号。
     */
    @TableField("loaded_version")
    private Integer loadedVersion;

    /**
     * 运行状态描述。
     */
    @TableField("runtime_status")
    private String runtimeStatus;

    /**
     * 最后上报时间。
     */
    @TableField("last_report_time")
    private LocalDateTime lastReportTime;

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
