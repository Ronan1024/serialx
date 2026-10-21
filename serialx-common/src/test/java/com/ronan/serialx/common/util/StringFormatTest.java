package com.ronan.serialx.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * {@link StringFormat} 单元测试。
 */
class StringFormatTest {

    @Test
    void shouldReplacePlaceholdersInOrder() {
        String result = StringFormat.format("namespace {} published version {}", "order_id", 3);

        assertEquals("namespace order_id published version 3", result);
    }

    @Test
    void shouldKeepRemainingPlaceholdersWhenArgsAreInsufficient() {
        String result = StringFormat.format("namespace {} published version {}", "order_id");

        assertEquals("namespace order_id published version {}", result);
    }

    @Test
    void shouldIgnoreExtraArgsWhenPlaceholdersAreInsufficient() {
        String result = StringFormat.format("namespace {}", "order_id", 3);

        assertEquals("namespace order_id", result);
    }

    @Test
    void shouldFormatNullArgument() {
        String result = StringFormat.format("namespace {}", (Object) null);

        assertEquals("namespace null", result);
    }

    @Test
    void shouldReturnOriginalTemplateWhenArgsAreEmpty() {
        String result = StringFormat.format("namespace {}");

        assertEquals("namespace {}", result);
    }

    @Test
    void shouldReturnNullWhenTemplateIsNull() {
        assertNull(StringFormat.format(null, "value"));
    }
}
