package com.ronan.serialx.admin.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Service 节点列表响应对象。
 */
@Getter
@Builder
public class ServiceInstanceNodeListResponse {

    /**
     * 节点汇总。
     */
    private ServiceInstanceNodeSummaryResponse summary;

    /**
     * 节点列表。
     */
    private List<ServiceInstanceNodeResponse> nodes;
}
