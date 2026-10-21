package com.ronan.serialx.admin.controller;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.ronan.serialx.admin.dto.AdminUserPageQueryRequest;
import com.ronan.serialx.admin.dto.AppPageQueryRequest;
import com.ronan.serialx.admin.dto.NamespacePageQueryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaginationControllerMappingTest {

    @Test
    void shouldExposeAllPaginationQueriesAsPost() {
        assertPagePost(AdminUserController.class, "pageUsers");
        assertPagePost(NamespaceController.class, "pageNamespaces");
        assertPagePost(AppController.class, "pageApps");
    }

    @Test
    void shouldReuseCommonPaginationRequest() {
        assertEquals("PageQueryRequest", AdminUserPageQueryRequest.class.getSuperclass().getSimpleName());
        assertEquals("PageQueryRequest", NamespacePageQueryRequest.class.getSuperclass().getSimpleName());
        assertEquals("PageQueryRequest", AppPageQueryRequest.class.getSuperclass().getSimpleName());

        AdminUserPageQueryRequest request = new AdminUserPageQueryRequest();
        assertEquals(1, request.getPageNo());
        assertEquals(20, request.getPageSize());
    }

    private void assertPagePost(Class<?> controllerType, String methodName) {
        Method method = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/page"}, mapping.value());
    }
}
