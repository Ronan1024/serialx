package com.ronan.serialx.common.node;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 续租和释放所需的完整身份；必须同时匹配运行会话及节点坐标。
 * 坐标使用包装类型，避免缺失字段被默认为合法的 0 号节点。
 *
 * @param runtimeId 注册时使用的运行会话 UUID
 * @param datacenterId 控制面分配的数据中心编号，范围 0～31
 * @param workerId 控制面分配的工作节点编号，范围 0～31
 */
public record NodeIdentityRequest(
        @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String runtimeId,
        @NotNull @Min(0) @Max(31) Integer datacenterId, @NotNull @Min(0) @Max(31) Integer workerId) {
}
