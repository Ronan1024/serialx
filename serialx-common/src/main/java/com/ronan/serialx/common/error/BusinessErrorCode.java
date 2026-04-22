package com.ronan.serialx.common.error;

import com.ronan.serialx.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 业务异常码，格式为 B+业务编号+四位编号。
 */
@Getter
@RequiredArgsConstructor
public enum BusinessErrorCode implements ErrorCode {

    /**
     * 后台请求参数非法。
     */
    ADMIN_PARAM_INVALID("B10010001", "invalid request parameter"),

    /**
     * 后台用户未认证。
     */
    ADMIN_UNAUTHORIZED("B10010002", "unauthorized"),

    /**
     * 后台用户无访问权限。
     */
    ADMIN_FORBIDDEN("B10010003", "forbidden"),

    /**
     * 后台用户不存在。
     */
    ADMIN_USER_NOT_FOUND("B10010004", "admin user not found"),

    /**
     * 后台用户名已存在。
     */
    ADMIN_USERNAME_EXISTS("B10010005", "username already exists"),

    /**
     * 后台登录失败。
     */
    ADMIN_LOGIN_FAILED("B10010006", "invalid username or password"),

    /**
     * 后台用户已禁用。
     */
    ADMIN_USER_DISABLED("B10010007", "admin user is disabled"),

    /**
     * 后台访问令牌无效。
     */
    ADMIN_TOKEN_INVALID("B10010008", "invalid token");

    /**
     * 业务异常码。
     */
    private final String code;

    /**
     * 默认异常消息。
     */
    private final String message;
}
