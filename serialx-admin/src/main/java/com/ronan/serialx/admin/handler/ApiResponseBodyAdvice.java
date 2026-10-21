package com.ronan.serialx.admin.handler;

import com.ronan.serialx.common.response.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 后台接口成功响应统一封装。
 */
@RestControllerAdvice(name = "adminApiResponseBodyAdvice", basePackages = "com.ronan.serialx.admin.controller")
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // shortcut: 当前后台接口仅返回 JSON，新增文件流或纯文本接口时需排除对应转换器
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        return body instanceof ApiResponse<?> ? body : ApiResponse.success(body);
    }
}
