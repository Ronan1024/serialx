package com.ronan.serialx.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 节点实例信息。
 */
@Data
public class ServiceInstanceInfo {

    /**
     * 实例 ID。
     */
    private String instanceId;

    /**
     * 主机名。
     */
    private String hostName;

    /**
     * IP 地址。
     */
    private String ip;

    /**
     * 当前进程运行时 ID。
     */
    private String runtimeId;

    /**
     * 端口。
     */
    private String port;

    /**
     * 实例运行状态。
     */
    private String status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 上一次上报时间。
     */
    private LocalDateTime lastReportTime;

    /**
     * 最近离线时间。
     */
    private LocalDateTime lastOfflineTime;
}
