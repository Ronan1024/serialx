package com.ronan.serialx.common.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import org.junit.jupiter.api.Test;

/**
 * {@link Assert} 单元测试。
 */
class AssertTest {

    @Test
    void shouldNotThrowWhenExpressionIsTrue() {
        assertDoesNotThrow(() -> Assert.isTrue(true, () -> new IllegalStateException("boom")));
    }

    @Test
    void shouldThrowCustomRuntimeExceptionWhenExpressionIsFalse() {
        var exception = assertThrows(
                IllegalStateException.class, () -> Assert.isTrue(false, () -> new IllegalStateException("boom")));

        assertEquals("boom", exception.getMessage());
    }

    @Test
    void shouldUseDefaultErrorMessageWhenThrowingBizException() {
        var exception = assertThrows(
                BizException.class, () -> Assert.isTrue(false, BusinessErrorCode.ADMIN_PARAM_INVALID));

        assertEquals(BusinessErrorCode.ADMIN_PARAM_INVALID.getCode(), exception.getCode());
        assertEquals(BusinessErrorCode.ADMIN_PARAM_INVALID.getMessage(), exception.getMessage());
    }

    @Test
    void shouldThrowForBlankText() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> Assert.hasText("   ", () -> new IllegalArgumentException("text required")));

        assertEquals("text required", exception.getMessage());
    }

    @Test
    void shouldThrowForNullObject() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> Assert.notNull(null, () -> new IllegalArgumentException("object required")));

        assertEquals("object required", exception.getMessage());
    }

    @Test
    void shouldRequireFalseExpression() {
        assertDoesNotThrow(() -> Assert.isFalse(false, BusinessErrorCode.ADMIN_PARAM_INVALID));
        assertThrows(BizException.class, () -> Assert.isFalse(true, BusinessErrorCode.ADMIN_PARAM_INVALID));
    }

    @Test
    void shouldAcceptNonNullObjectAndNonBlankText() {
        assertDoesNotThrow(() -> Assert.notNull(new Object(), () -> new IllegalArgumentException("null")));
        assertDoesNotThrow(() -> Assert.hasText(" value ", () -> new IllegalArgumentException("blank")));
        assertThrows(IllegalArgumentException.class,
                () -> Assert.hasText(null, () -> new IllegalArgumentException("blank")));
    }

    @Test
    void shouldKeepIsNullRejectingNullForCompatibility() {
        assertDoesNotThrow(() -> Assert.isNull(new Object(), () -> new IllegalArgumentException("null")));
        assertThrows(IllegalArgumentException.class,
                () -> Assert.isNull(null, () -> new IllegalArgumentException("null")));
    }
}
