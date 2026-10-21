package com.ronan.serialx.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminUserRoleEnumTest {

    @Test
    void shouldExposeStableRoleCodes() {
        assertEquals("ADMIN", AdminUserRoleEnum.ADMIN.getCode());
        assertEquals("OPERATOR", AdminUserRoleEnum.OPERATOR.getCode());
        assertEquals("VIEWER", AdminUserRoleEnum.VIEWER.getCode());
    }

    @Test
    void shouldFindRoleInCommaSeparatedRoleList() {
        assertTrue(AdminUserRoleEnum.ADMIN.isIncludedIn("VIEWER, ADMIN"));
        assertFalse(AdminUserRoleEnum.ADMIN.isIncludedIn("OPERATOR"));
        assertFalse(AdminUserRoleEnum.ADMIN.isIncludedIn(null));
    }
}
