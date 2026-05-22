package com.ronan.serialx.sdk.exception;

/**
 * 服务端业务失败异常。
 */
public class SerialxRemoteException extends SerialxException {

    public SerialxRemoteException(
            String message,
            String namespaceCode,
            int attempts,
            String endpoint,
            String responseCode,
            String responseMessage,
            String clientName,
            String clientId,
            String instanceId) {
        super(message, null, namespaceCode, attempts, endpoint, null, responseCode + ":" + responseMessage, clientName, clientId, instanceId);
    }
}
