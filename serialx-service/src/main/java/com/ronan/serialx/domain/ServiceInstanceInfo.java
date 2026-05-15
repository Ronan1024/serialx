package com.ronan.serialx.domain;

import lombok.Data;

/**
 * @program: serialx
 * @description:
 * @author: L.J.Ran
 * @create: 2026/5/15
 */
@Data
public class ServiceInstanceInfo {

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 主机
     */
    private String hostName;

    /**
     * ip
     */
    private String ip;

    /**
     * 运行时id
     */
    private String runtimeId;


    /**
     * 端口
     */
    private String port;

    /**
     * 创建时间
     */
    private String createTime;


    /**
     * 上一次上报时间
     */
    private String lastReportTime;





}
