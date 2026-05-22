package com.ronan.serialx.sdk.transport;

import java.util.Set;

/**
 * 请求节点选择器。
 */
public class EndpointSelector {

    private final EndpointRegistry endpointRegistry;

    public EndpointSelector(EndpointRegistry endpointRegistry) {
        this.endpointRegistry = endpointRegistry;
    }

    public String select(Set<String> excluded) {
        return endpointRegistry.select(excluded);
    }
}
