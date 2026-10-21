package com.ronan.serialx.api.controller;

import com.ronan.serialx.api.dto.IdHealthResponse;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import com.ronan.serialx.application.node.SnowflakeNodeLifecycle;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发号服务健康检查接口。
 */
@RestController
@RequestMapping("/api/ids")
@RequiredArgsConstructor
public class IdHealthController {

    private final NamespaceRegistry namespaceRegistry;

    private final ServiceInstanceIdentity serviceInstanceIdentity;

    private final SnowflakeNodeLifecycle nodeLifecycle;

    /**
     * 查询当前 service 节点发号健康状态。
     */
    @GetMapping("/health")
    public ResponseEntity<IdHealthResponse> health() {
        int loadedNamespaceCount = namespaceRegistry.listAll().size();
        boolean available = nodeLifecycle.isAvailable()
                && namespaceRegistry.listAll().stream().anyMatch(NamespaceRuntime::isAvailable);
        return ResponseEntity.status(available ? 200 : 503).body(IdHealthResponse.builder()
                .available(available)
                .nodeId(serviceInstanceIdentity.getInstanceId())
                .loadedNamespaceCount(loadedNamespaceCount)
                .timestamp(System.currentTimeMillis())
                .build());
    }
}
