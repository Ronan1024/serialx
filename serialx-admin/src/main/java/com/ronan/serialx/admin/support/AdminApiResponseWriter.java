package com.ronan.serialx.admin.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.exception.ErrorCode;
import com.ronan.serialx.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * 后台接口 JSON 响应写入工具。
 */
@Component
@RequiredArgsConstructor
public class AdminApiResponseWriter {

    /**
     * JSON 序列化组件。
     */
    private final ObjectMapper objectMapper;

    /**
     * 写入失败响应。
     */
    public void writeFailure(HttpServletResponse response, int httpStatus, ErrorCode errorCode) throws IOException {
        writeFailure(response, httpStatus, errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 写入失败响应。
     */
    public void writeFailure(HttpServletResponse response, int httpStatus, String code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }
}
