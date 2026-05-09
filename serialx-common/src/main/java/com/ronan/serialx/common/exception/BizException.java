package com.ronan.serialx.common.exception;

import lombok.Getter;

import java.util.function.Supplier;

/**
 * 业务异常，携带可返回给客户端的错误码。
 */
@Getter
public class BizException extends RuntimeException {

    /**
     * 异常码枚举。
     */
    private final ErrorCode errorCode;

    /**
     * HTTP 状态码。
     */
    private final int httpStatus;

    /**
     * 创建业务异常。
     */
    public BizException(ErrorCode errorCode, int httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }



    /**
     * 创建业务异常。
     */
    public BizException(String errorCode, String message) {
        super(message);
        this.errorCode = new ErrorCode() {
            @Override
            public String getCode() {
                return errorCode;
            }

            @Override
            public String getMessage() {
                return message;
            }
        };

    }

    /**
     * 获取异常码。
     */
    public String getCode() {
        return errorCode.getCode();
    }

    public static Supplier<? extends RuntimeException> supplier(ErrorCode errorCode){
        return ()-> new BizException(errorCode);
    }

    public static Supplier<? extends RuntimeException> supplier(String code, String message){
        return ()-> new BizException(code, message);
    }
}
