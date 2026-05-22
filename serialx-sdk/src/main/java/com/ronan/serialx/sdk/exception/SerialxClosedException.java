package com.ronan.serialx.sdk.exception;

/**
 * 客户端已关闭异常。
 */
public class SerialxClosedException extends SerialxException {

    public SerialxClosedException(String message) {
        super(message);
    }
}
