package com.ronan.serialx.sdk.exception;

/**
 * HTTP 状态异常。
 */
public class SerialxHttpException extends SerialxException {

    public SerialxHttpException(
            String message,
            Throwable cause,
            String namespaceCode,
            int attempts,
            String endpoint,
            Integer responseCode,
            String responseMessage,
            String clientName,
            String clientId,
            String instanceId) {
        super(message, cause, namespaceCode, attempts, endpoint, responseCode, responseMessage, clientName, clientId, instanceId);
    }
}
