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
    ADMIN_TOKEN_INVALID("B10010008", "invalid token"),

    /**
     * Namespace 不存在。
     */
    NAMESPACE_NOT_FOUND("B10020001", "namespace not found"),

    /**
     * Namespace 编码已存在。
     */
    NAMESPACE_CODE_EXISTS("B10020002", "namespace code already exists"),

    /**
     * Namespace 状态非法。
     */
    NAMESPACE_STATUS_INVALID("B10020003", "namespace status invalid"),

    /**
     * Namespace 配置非法。
     */
    NAMESPACE_CONFIG_INVALID("B10020004", "namespace config invalid"),

    /**
     * Namespace 发布失败。
     */
    NAMESPACE_PUBLISH_FAILED("B10020005", "namespace publish failed"),

    /**
     * Namespace 回滚失败。
     */
    NAMESPACE_ROLLBACK_FAILED("B10020006", "namespace rollback failed"),

    /**
     * Namespace 删除被禁止。
     */
    NAMESPACE_DELETE_FORBIDDEN("B10020007", "namespace delete forbidden"),

    /**
     * Namespace 版本不存在。
     */
    NAMESPACE_VERSION_NOT_FOUND("B10020008", "namespace version not found");

    /**
     * 业务异常码。
     */
    private final String code;

    /**
     * 默认异常消息。
     */
    private final String message;
}
