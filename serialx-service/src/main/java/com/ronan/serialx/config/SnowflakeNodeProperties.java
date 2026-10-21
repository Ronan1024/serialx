package com.ronan.serialx.config;

import static com.ronan.serialx.common.node.NodeTiming.LEASE_DURATION;
import static com.ronan.serialx.common.node.NodeTiming.RENEW_INTERVAL_SECONDS;
import static com.ronan.serialx.common.node.NodeTiming.SAFETY_MARGIN;

import com.ronan.serialx.common.util.Assert;
import com.ronan.serialx.common.util.StringFormat;
import com.ronan.serialx.infra.http.OutboundHttpProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * 雪花控制面接入配置；节点身份由租约提供，安全预算采用系统策略。
 */
@Data
@Component
@ConfigurationProperties(prefix = "serialx.service.snowflake")
public class SnowflakeNodeProperties {

    private static final int MIN_STARTUP_ATTEMPTS = 1;
    private static final int MAX_STARTUP_ATTEMPTS = 10;

    @Getter(AccessLevel.NONE)
    private final OutboundHttpProperties outboundHttpProperties;

    @Autowired
    public SnowflakeNodeProperties(OutboundHttpProperties outboundHttpProperties) {
        this.outboundHttpProperties = outboundHttpProperties;
    }

    /**
     * 控制面基地址，可包含基础路径；跨主机部署须使用 HTTPS。
     */
    private String adminUrl = "http://localhost:8080";
    /**
     * 令牌
     */
    private String machineToken;
    /**
     * 最大尝试次数。
     */
    private int startupAttempts = 3;

    /**
     * Spring 完成配置绑定后自动自检；非法凭证、地址或时间预算直接阻止服务启动。
     */
    @PostConstruct
    public void validate() {
        validateAdminUrl();
        Assert.hasText(machineToken, () -> new IllegalArgumentException("节点机器令牌不能为空"));

        outboundHttpProperties.validate();
        Duration requiredBudget = Duration.ofSeconds(RENEW_INTERVAL_SECONDS)
                .plus(outboundHttpProperties.getRequestTimeout()).plus(SAFETY_MARGIN);
        Assert.isTrue(requiredBudget.compareTo(LEASE_DURATION) < 0,
                () -> new IllegalArgumentException("系统续租周期、统一请求超时与安全余量之和必须小于系统租期"));
        Assert.isTrue(startupAttempts >= MIN_STARTUP_ATTEMPTS && startupAttempts <= MAX_STARTUP_ATTEMPTS,
                () -> new IllegalArgumentException(StringFormat.format("startup-attempts 必须在 {} 至 {} 之间",
                        MIN_STARTUP_ATTEMPTS, MAX_STARTUP_ATTEMPTS)));
    }

    private void validateAdminUrl() {
        Assert.hasText(adminUrl, () -> new IllegalArgumentException("控制面地址不能为空"));
        URI uri;
        try {
            uri = URI.create(adminUrl);
        } catch (IllegalArgumentException ignored) {
            // 不保留解析异常，避免非法地址中的内嵌凭证进入启动日志。
            throw new IllegalArgumentException("控制面地址格式无效");
        }
        Assert.isTrue(("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null && (uri.getPort() == -1 || uri.getPort() > 0 && uri.getPort() <= 65535),
                () -> new IllegalArgumentException("控制面地址必须使用 HTTP 或 HTTPS，且包含有效的主机和端口"));
        Assert.isTrue(uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null,
                () -> new IllegalArgumentException("控制面基地址不能包含凭证、查询参数或片段"));

        int end = adminUrl.length();
        while (end > 0 && adminUrl.charAt(end - 1) == '/') {
            end--;
        }
        adminUrl = adminUrl.substring(0, end);
    }
}
