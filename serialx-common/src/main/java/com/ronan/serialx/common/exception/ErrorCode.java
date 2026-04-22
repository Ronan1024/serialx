package com.ronan.serialx.common.exception;

/**
 * 异常码基础接口。
 */
public interface ErrorCode {

    /**
     * 获取异常码。
     */
    String getCode();

    /**
     * 获取默认异常消息。
     */
    String getMessage();
}
