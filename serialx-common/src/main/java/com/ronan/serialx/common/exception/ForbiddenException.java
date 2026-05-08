package com.ronan.serialx.common.exception;

import lombok.Getter;

/**
 * 无权限异常。
 */
@Getter
public class ForbiddenException extends RuntimeException {

    /**
     * 异常码枚举。
     */
    private final ErrorCode errorCode;

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 获取异常码。
     */
    public String getCode() {
        return errorCode.getCode();
    }
}
