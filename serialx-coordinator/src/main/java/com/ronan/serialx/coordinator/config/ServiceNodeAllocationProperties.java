package com.ronan.serialx.coordinator.config;

import static com.ronan.serialx.common.node.NodeTiming.LEASE_DURATION;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * 节点分配启动配置，绑定 serialx.admin.node-allocation；修改后需重新加载应用配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "serialx.admin.node-allocation")
public class ServiceNodeAllocationProperties {
    /**
     * 独立随机机器令牌，配置引用 SERIALX_SERVICE_NODE_TOKEN；至少 32 UTF-8 字节，不复用 JWT 密钥，不记录日志。
     */
    private String machineToken;
    /**
     * 迁移期保留共享令牌 HTTP 接口；关闭后允许仅使用独立 mTLS 身份。
     */
    private boolean legacyHttpEnabled = true;

    /**
     * HTTP 兼容的硬截止时间（UTC）；上线时应配置实际迁移期限。
     */
    private Instant legacyHttpUntil = Instant.parse("2027-01-01T00:00:00Z");

    /**
     * 相对主库当前时间预留的发号窗口，不能短于租期；窗口越长，节点安全复用等待越长。
     */
    private Duration reservedWindow = LEASE_DURATION;

    /**
     * 启动及进程内装配时校验安全约束；异常不包含机器令牌原文。
     */
    @PostConstruct
    public void validate() {
        if (legacyHttpEnabled && (machineToken == null || machineToken.isBlank()
                || machineToken.getBytes(StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalArgumentException("节点机器令牌必须通过环境变量提供且至少 32 字节");
        }
        if (legacyHttpUntil == null) {
            throw new IllegalArgumentException("旧节点 HTTP 接口必须设置明确迁移截止时间");
        }
        if (reservedWindow == null || reservedWindow.compareTo(LEASE_DURATION) < 0) {
            throw new IllegalArgumentException("节点预留窗口不能短于系统租期");
        }
    }

    /**
     * 每次请求实时检查截止时间，进程无需重启即可停止接受迁移期凭证。
     */
    public boolean legacyHttpAvailable() {
        return legacyHttpEnabled && Instant.now().isBefore(legacyHttpUntil);
    }
}
