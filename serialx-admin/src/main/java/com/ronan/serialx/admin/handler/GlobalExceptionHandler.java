package com.ronan.serialx.admin.handler;

import java.util.stream.Collectors;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.exception.ForbiddenException;
import com.ronan.serialx.common.error.SystemErrorCode;
import com.ronan.serialx.common.exception.UnauthorizedException;
import com.ronan.serialx.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 后台接口全局异常处理器。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        log.warn("admin biz exception, code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.ok(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理未认证异常。
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("admin unauthorized exception, code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(401).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理无权限异常。
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        log.warn("admin forbidden exception, code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(403).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理请求体参数校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("admin request body validation failed, message={}", message);
        return ResponseEntity.ok(ApiResponse.fail(BusinessErrorCode.ADMIN_PARAM_INVALID.getCode(), message));
    }

    /**
     * 处理请求参数校验异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("admin request parameter validation failed, message={}", ex.getMessage());
        return ResponseEntity.ok(ApiResponse.fail(BusinessErrorCode.ADMIN_PARAM_INVALID.getCode(), ex.getMessage()));
    }

    /**
     * 处理权限不足异常。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("admin access denied, message={}", ex.getMessage());
        return ResponseEntity.status(403)
                .body(ApiResponse.fail(
                        BusinessErrorCode.ADMIN_FORBIDDEN.getCode(), BusinessErrorCode.ADMIN_FORBIDDEN.getMessage()));
    }

    /**
     * 处理未预期异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("admin unexpected exception", ex);
        return ResponseEntity.ok()
                .body(ApiResponse.fail(
                        SystemErrorCode.INTERNAL_ERROR.getCode(), SystemErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
