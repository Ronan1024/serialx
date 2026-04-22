package com.ronan.serialx.common.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 统一接口响应对象。
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    /**
     * 响应码，成功为 0，失败为业务或系统异常码。
     */
    private String code;

    /**
     * 响应消息。
     */
    private String message;

    /**
     * 响应数据。
     */
    private T data;

    /**
     * 构造成功响应。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data);
    }

    /**
     * 构造无数据成功响应。
     */
    public static ApiResponse<Void> success() {
        return success(null);
    }

    /**
     * 构造失败响应。
     */
    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
