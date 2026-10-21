package com.ronan.serialx.common.model;

import lombok.Data;
import java.time.LocalDateTime;

/** service 和 admin 共用的实例快照；仅用于运行态展示，不代表主库节点租约。 */
@Data
public class ServiceInstanceSnapshot {
    /** 实例展示标识。 */
    private String instanceId;
    /** 本次进程启动 UUID。 */
    private String runtimeId;
    /** 主机名。 */
    private String hostName;
    /** 对外上报的 IP。 */
    private String ip;
    /** 对外上报的端口。 */
    private String port;
    /** 实例运行状态。 */
    private String status;
    /** 创建时间，不包含时区。 */
    private LocalDateTime createTime;
    /** 最后上报时间，不包含时区。 */
    private LocalDateTime lastReportTime;
    /** 最近离线时间，不包含时区。 */
    private LocalDateTime lastOfflineTime;
}
