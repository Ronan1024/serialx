package com.ronan.serialx.common.exception;

import lombok.Getter;

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
     * 获取异常码。
     */
    public String getCode() {
        return errorCode.getCode();
    }

    /**
     * 创建请求参数错误异常。
     */
    public static BizException badRequest(ErrorCode errorCode, String message) {
        return new BizException(errorCode, 400, message);
    }

    /**
     * 创建未认证异常。
     */
    public static BizException unauthorized(ErrorCode errorCode, String message) {
        return new BizException(errorCode, 401, message);
    }

    /**
     * 创建无权限异常。
     */
    public static BizException forbidden(ErrorCode errorCode, String message) {
        return new BizException(errorCode, 403, message);
    }

    /**
     * 创建资源不存在异常。
     */
    public static BizException notFound(ErrorCode errorCode, String message) {
        return new BizException(errorCode, 404, message);
    }

    /**
     * 创建资源冲突异常。
     */
    public static BizException conflict(ErrorCode errorCode, String message) {
        return new BizException(errorCode, 409, message);
    }

    /**
     * 创建系统配置异常。
     */
    public static BizException system(ErrorCode errorCode, String message) {
        return new BizException(errorCode, 500, message);
    }
}
