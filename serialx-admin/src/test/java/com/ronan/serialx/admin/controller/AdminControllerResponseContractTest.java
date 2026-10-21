package com.ronan.serialx.admin.controller;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.ronan.serialx.admin.handler.ApiResponseBodyAdvice;
import com.ronan.serialx.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminControllerResponseContractTest {

    @Test
    void shouldLeaveApiResponseWrappingToGlobalAdvice() {
        List.of(
                        AdminAuthController.class,
                        AdminUserController.class,
                        AppController.class,
                        CallStatsController.class,
                        NamespaceController.class,
                        ServiceInstanceNodeController.class)
                .forEach(this::assertNoManualApiResponse);
    }

    @Test
    void shouldWrapBusinessDataAndEmptyResponsesWithoutDoubleWrapping() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ResponseController())
                .setControllerAdvice(new ApiResponseBodyAdvice())
                .build();

        mockMvc.perform(get("/response/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.name").value("serialx"));
        mockMvc.perform(delete("/response/empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(get("/response/wrapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("ready"));
    }

    private void assertNoManualApiResponse(Class<?> controllerType) {
        Arrays.stream(controllerType.getDeclaredMethods())
                .filter(this::isRequestHandler)
                .forEach(method -> assertNotEquals(
                        ApiResponse.class,
                        method.getReturnType(),
                        () -> controllerType.getSimpleName() + "." + method.getName()));
    }

    private boolean isRequestHandler(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null;
    }

    @RestController
    @RequestMapping("/response")
    static class ResponseController {

        @GetMapping("/data")
        Map<String, String> data() {
            return Map.of("name", "serialx");
        }

        @DeleteMapping("/empty")
        void empty() {
        }

        @GetMapping("/wrapped")
        ApiResponse<String> wrapped() {
            return ApiResponse.success("ready");
        }
    }
}
