package com.ronan.serialx.common.error;

import com.ronan.serialx.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 系统异常码，格式为 S+四位编号。
 */
@Getter
@RequiredArgsConstructor
public enum SystemErrorCode implements ErrorCode {

    /**
     * 系统内部异常。
     */
    INTERNAL_ERROR("S0001", "internal server error"),

    /**
     * 系统配置异常。
     */
    CONFIG_ERROR("S0002", "configuration error");

    /**
     * 系统异常码。
     */
    private final String code;

    /**
     * 默认异常消息。
     */
    private final String message;
}
