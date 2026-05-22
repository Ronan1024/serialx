package com.ronan.serialx.api.controller;

import com.ronan.serialx.api.dto.IdHealthResponse;
import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.support.ServiceInstanceIdentity;
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

    /**
     * 查询当前 service 节点发号健康状态。
     */
    @GetMapping("/health")
    public ApiResponse<IdHealthResponse> health() {
        int loadedNamespaceCount = namespaceRegistry.listAll().size();
        return ApiResponse.success(IdHealthResponse.builder()
                .available(true)
                .nodeId(serviceInstanceIdentity.getInstanceId())
                .loadedNamespaceCount(loadedNamespaceCount)
                .timestamp(System.currentTimeMillis())
                .build());
    }
}
