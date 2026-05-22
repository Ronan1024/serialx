package com.ronan.serialx.sdk.exception;

/**
 * 没有可用节点异常。
 */
public class SerialxNoAvailableEndpointException extends SerialxException {

    public SerialxNoAvailableEndpointException(
            String message,
            Throwable cause,
            String namespaceCode,
            int attempts,
            String endpoint,
            String clientName,
            String clientId,
            String instanceId) {
        super(message, cause, namespaceCode, attempts, endpoint, null, null, clientName, clientId, instanceId);
    }
}
