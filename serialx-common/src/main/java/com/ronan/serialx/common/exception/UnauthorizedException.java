package com.ronan.serialx.common.exception;

import lombok.Getter;

/**
 * 未认证异常。
 */
@Getter
public class UnauthorizedException extends RuntimeException {

    /**
     * 异常码枚举。
     */
    private final ErrorCode errorCode;

    public UnauthorizedException(ErrorCode errorCode, String message) {
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
