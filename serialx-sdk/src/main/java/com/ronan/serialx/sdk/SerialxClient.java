package com.ronan.serialx.sdk;

import java.util.List;

import com.ronan.serialx.sdk.config.SerialxClientConfig;
import com.ronan.serialx.sdk.model.EndpointStatus;
import com.ronan.serialx.sdk.model.NamespaceRuntimeStatus;

/**
 * SerialX Java 同步客户端。
 */
public interface SerialxClient extends AutoCloseable {

    /**
     * 创建客户端构建器。
     */
    static SerialxClientBuilder builder() {
        return new SerialxClientBuilder();
    }

    /**
     * 通过配置创建客户端。
     */
    static SerialxClient create(SerialxClientConfig config) {
        return new DefaultSerialxClient(config);
    }

    /**
     * 获取单个 ID。
     */
    String nextId(String namespaceCode);

    /**
     * 批量获取 ID。
     */
    List<String> batchNextId(String namespaceCode, int count);

    /**
     * 查询 Namespace 运行时状态。
     */
    NamespaceRuntimeStatus getRuntimeStatus(String namespaceCode);

    /**
     * 查询 SDK 观察到的服务节点状态。
     */
    List<EndpointStatus> getEndpointStatuses();

    @Override
    void close();
}
