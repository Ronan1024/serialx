package com.ronan.serialx.common.util;

import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.exception.ErrorCode;
import java.util.function.Supplier;

/**
 * 断言工具类
 */
public final class Assert {

    private Assert() {
    }

    /**
     * 断言表达式为 true，否则抛出自定义异常。
     */
    public static void isTrue(boolean expression, Supplier<? extends RuntimeException> exceptionSupplier) {
        if (!expression) {
            throw exceptionSupplier.get();
        }
    }


    /**
     * 断言表达式为 true，否则抛出带自定义消息的业务异常。
     */
    public static void isTrue(boolean expression, ErrorCode errorCode) {
        isTrue(expression,  BizException.supplier(errorCode));
    }

    /**
     * 断言表达式为 false，否则抛出自定义异常。
     */
    public static void isFalse(boolean expression, Supplier<? extends RuntimeException> exceptionSupplier) {
        isTrue(!expression, exceptionSupplier);
    }

    /**
     * 断言表达式为 false，否则抛出业务异常。
     */
    public static void isFalse(boolean expression, ErrorCode errorCode) {
        isFalse(expression, BizException.supplier(errorCode));
    }

    /**
     * 断言对象不为 null，否则抛出自定义异常。
     */
    public static void notNull(Object object, Supplier<? extends RuntimeException> exceptionSupplier) {
        isTrue(object != null, exceptionSupplier);
    }

    /**
     * 断言对象为 null ， 则抛出自定义异常
     */
    public static void  isNull(Object object, Supplier<? extends RuntimeException> exceptionSupplier) {
        isTrue(object == null, exceptionSupplier);
    }

    /**
     * 断言字符串包含非空白字符，否则抛出自定义异常。
     */
    public static void hasText(String text, Supplier<? extends RuntimeException> exceptionSupplier) {
        isTrue(text != null && !text.trim().isEmpty(), exceptionSupplier);
    }
}
