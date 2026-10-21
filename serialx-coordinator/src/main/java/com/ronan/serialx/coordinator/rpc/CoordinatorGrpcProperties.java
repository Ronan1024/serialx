package com.ronan.serialx.coordinator.rpc;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 协调 RPC 独立端口、执行资源和证书身份配置；关闭时不要求本地开发环境准备证书。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "serialx.coordinator.grpc")
public class CoordinatorGrpcProperties {
    /** 默认关闭；启用前必须同时准备 TLS 文件和机器身份授权列表。 */
    private boolean enabled;
    /** 本地默认只监听回环地址；生产跨机器访问需显式配置监听地址。 */
    private String bindAddress = "127.0.0.1";
    /** 独立于后台 HTTP 的协调服务监听端口。 */
    private int port = 9090;
    /** 本进程服务的唯一发号域；不匹配域的请求拒绝，不能误访问其他节点池。 */
    private String allocationDomain = "default";
    /** PEM 服务端证书链、未加密私钥和可信客户端 CA；不能配置为跳过校验。 */
    private Path certificateChain;
    /** 服务端 PEM 私钥路径；配置和日志不得包含私钥原文。 */
    private Path privateKey;
    /** 信任的客户端 CA 证书路径；不是允许任意证书的开关。 */
    private Path trustCertificate;
    /** 协调业务工作线程数，也是已开始执行的同步协调用例的并发上限。 */
    private int workerThreads = 16;
    /** 工作执行器最多缓存的任务数；饱和后拒绝提交，不创建无限队列或借用 HTTP 线程。 */
    private int queueCapacity = 64;
    /** 实际协调用例并发预算；即使 RPC deadline 已过，也须等底层处理退出才归还。 */
    private int maxInFlight = 64;
    /** 单条入站 Protobuf 消息大小上限，单位为字节。 */
    private int maxInboundMessageSize = 64 * 1024;
    /** 从进入拦截器开始的响应预算；不包含之前的传输/执行器排队，客户端仍须设置总 deadline。 */
    private Duration maxRequestDuration = Duration.ofSeconds(5);
    /** 进程关闭时等待已接收 RPC 正常完成的最长时间，之后停止网络接入。 */
    private Duration shutdownGrace = Duration.ofSeconds(5);
    /** 别名只方便配置绑定；认证以证书 URI SAN 的完整值精确匹配，绝不使用 CN 回退。 */
    private Map<String, MachineIdentity> identities = new LinkedHashMap<>();

    public enum Role { NODE, DISCOVERY }

    /** 同一 URI 在所有 admin 实例上必须映射到相同权限；证书轮换保持 URI 不变。 */
    @Getter
    @Setter
    public static class MachineIdentity {
        /** 可信叶子证书中的 URI SAN，使用完整字符串精确匹配。 */
        private String certificateUri;
        /** NODE 身份获准使用的唯一 instanceId，不能由请求正文覆盖。 */
        private String instanceId;
        /** 节点协调与客户端发现分开授予；后台 JWT 不能替代此角色。 */
        private Set<Role> roles = new LinkedHashSet<>();
        /** DISCOVERY 可查询的精确 namespace 集合，不提供隐式通配或默认全权限。 */
        private Set<String> allowedNamespaces = new LinkedHashSet<>();
    }

    /** 启用时失败关闭，不允许证书缺失导致降级为明文或共享令牌 RPC。 */
    @PostConstruct
    public void validate() {
        if (!enabled) return;
        if (bindAddress == null || bindAddress.isBlank() || port < 1 || port > 65535
                || allocationDomain == null || allocationDomain.isBlank()
                || workerThreads < 1 || queueCapacity < 1 || maxInFlight < 1
                || maxInboundMessageSize < 1 || maxRequestDuration == null
                || maxRequestDuration.isNegative() || maxRequestDuration.isZero()
                || maxRequestDuration.compareTo(Duration.ofSeconds(5)) > 0
                || shutdownGrace == null || shutdownGrace.isNegative()) {
            throw new IllegalArgumentException("协调 RPC 配置非法；请求上限必须大于 0 且不超过 5 秒");
        }
        requireFile(certificateChain);
        requireFile(privateKey);
        requireFile(trustCertificate);
        if (identities == null || identities.isEmpty()) throw new IllegalArgumentException("协调 RPC 必须配置机器身份");
        Set<String> uris = new LinkedHashSet<>();
        for (MachineIdentity identity : identities.values()) {
            if (identity == null || identity.getCertificateUri() == null || identity.getCertificateUri().isBlank()
                    || !uris.add(identity.getCertificateUri()) || identity.getRoles() == null
                    || identity.getRoles().isEmpty() || identity.getRoles().contains(null)
                    || identity.getAllowedNamespaces() == null
                    || identity.getAllowedNamespaces().stream().anyMatch(value -> value == null || value.isBlank())
                    || (identity.getRoles().contains(Role.NODE) && (identity.getInstanceId() == null
                        || identity.getInstanceId().isBlank() || identity.getInstanceId().length() > 128))) {
                throw new IllegalArgumentException("协调 RPC 机器身份必须唯一且具有明确角色和实例授权");
            }
        }
    }

    private static void requireFile(Path value) {
        if (value == null || !Files.isRegularFile(value) || !Files.isReadable(value)) {
            throw new IllegalArgumentException("协调 RPC 证书文件缺失或不可读");
        }
    }
}
