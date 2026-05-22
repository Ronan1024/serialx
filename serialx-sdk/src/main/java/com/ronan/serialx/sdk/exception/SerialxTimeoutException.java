package com.ronan.serialx.sdk.exception;

/**
 * 请求超时异常。
 */
public class SerialxTimeoutException extends SerialxException {

    public SerialxTimeoutException(
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
