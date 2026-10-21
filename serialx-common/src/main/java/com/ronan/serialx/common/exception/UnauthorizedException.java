package com.ronan.serialx.common.exception;

import lombok.Getter;

import java.util.function.Supplier;

/**
 * 未认证异常。
 */
@Getter
public class UnauthorizedException extends RuntimeException {

    /**
     * 异常码枚举。
     */
    private final ErrorCode errorCode;

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }


    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static Supplier<? extends RuntimeException> supplier(ErrorCode errorCode){
        return () -> new UnauthorizedException(errorCode);
    }
    /**
     * 获取异常码。
     */
    public String getCode() {
        return errorCode.getCode();
    }
}
