package com.ronan.serialx.common.node;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import lombok.Getter;

/**
 * 内部分配 API 使用非成功 HTTP 状态表达重试和身份失效。
 */
@Getter
public class NodeAllocationException extends BizException {
    /**
     * 内部接口的失败 HTTP 状态；不能按普通业务异常包装成 HTTP 200。
     */
    private final int httpStatus;

    /**
     * @param code       可向调用方公开的业务错误码
     * @param httpStatus 对应的非成功 HTTP 状态，如 400、409 或 503
     */
    public NodeAllocationException(BusinessErrorCode code, int httpStatus) {
        super(code);
        this.httpStatus = httpStatus;
    }
}
