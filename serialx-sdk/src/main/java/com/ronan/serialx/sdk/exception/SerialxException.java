package com.ronan.serialx.sdk.exception;

/**
 * SerialX SDK 基类异常。
 */
public class SerialxException extends RuntimeException {

    private final String namespaceCode;

    private final String endpoint;

    private final int attempts;

    private final Integer responseCode;

    private final String responseMessage;

    private final String clientName;

    private final String clientId;

    private final String instanceId;

    public SerialxException(String message) {
        this(message, null, null, 0, null, null, null, null, null, null);
    }

    public SerialxException(String message, Throwable cause) {
        this(message, cause, null, 0, null, null, null, null, null, null);
    }

    public SerialxException(
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
        super(message, cause);
        this.namespaceCode = namespaceCode;
        this.attempts = attempts;
        this.endpoint = endpoint;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.clientName = clientName;
        this.clientId = clientId;
        this.instanceId = instanceId;
    }

    public String getNamespaceCode() {
        return namespaceCode;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public int getAttempts() {
        return attempts;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientId() {
        return clientId;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
