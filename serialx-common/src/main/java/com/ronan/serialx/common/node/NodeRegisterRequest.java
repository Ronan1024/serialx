package com.ronan.serialx.common.node;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册节点会话，由控制面选择节点，不接受人工指定的节点编号。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeRegisterRequest {

    /**
     * 每次启动生成的小写标准 UUID，同一次启动重试必须保持不变。
     */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private String runtimeId;

    /**
     * 运维展示与注册一致性校验标识，不参与雪花编码，也不能替代 runtimeId。
     */
    @NotBlank
    @Size(max = 128)
    private String instanceId;
}
