package com.ronan.serialx.common.exception;

import lombok.Getter;

import java.util.function.Supplier;

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

    public static Supplier<? extends RuntimeException> supplier(ErrorCode errorCode) {
        return () -> new ForbiddenException(errorCode, errorCode.getMessage());
    }

    /**
     * 获取异常码。
     */
    public String getCode() {
        return errorCode.getCode();
    }
}
