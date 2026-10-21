package com.ronan.serialx.admin.controller;

import com.ronan.serialx.admin.dto.ServiceInstanceNodeListResponse;
import com.ronan.serialx.admin.manager.ServiceInstanceNodeManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service 节点管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/service-instances")
public class ServiceInstanceNodeController {

    /**
     * Service 节点业务编排。
     */
    private final ServiceInstanceNodeManager serviceInstanceNodeManager;

    /**
     * 查询 Service 节点列表。
     */
    @GetMapping("/nodes")
    public ServiceInstanceNodeListResponse listNodes() {
        return serviceInstanceNodeManager.listNodes();
    }
}
