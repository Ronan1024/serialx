package com.ronan.serialx.admin.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.error.SystemErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.exception.ForbiddenException;
import com.ronan.serialx.common.exception.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * GlobalExceptionHandler 单元测试。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn200ForNonAuthBizException() {
        var response = handler.handleBizException(new BizException(BusinessErrorCode.ADMIN_PARAM_INVALID.getCode(), "invalid"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(BusinessErrorCode.ADMIN_PARAM_INVALID.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldReturn401ForUnauthorizedBizException() {
        var response = handler.handleUnauthorized(
                new UnauthorizedException(BusinessErrorCode.ADMIN_UNAUTHORIZED, "unauthorized"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals(BusinessErrorCode.ADMIN_UNAUTHORIZED.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldReturn403ForForbiddenException() {
        var response = handler.handleForbidden(
                new ForbiddenException(BusinessErrorCode.ADMIN_USER_DISABLED, "forbidden"));

        assertEquals(403, response.getStatusCode().value());
        assertEquals(BusinessErrorCode.ADMIN_USER_DISABLED.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldReturn403ForAccessDeniedException() {
        var response = handler.handleAccessDenied(new AccessDeniedException("forbidden"));

        assertEquals(403, response.getStatusCode().value());
        assertEquals(BusinessErrorCode.ADMIN_FORBIDDEN.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldReturn200ForConstraintViolationException() {
        var response = handler.handleConstraintViolation(new ConstraintViolationException("bad request", null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(BusinessErrorCode.ADMIN_PARAM_INVALID.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldReturn200ForUnexpectedException() {
        var response = handler.handleException(new RuntimeException("boom"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(SystemErrorCode.INTERNAL_ERROR.getCode(), response.getBody().getCode());
    }
}
