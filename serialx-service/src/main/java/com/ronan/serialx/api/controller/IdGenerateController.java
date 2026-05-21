package com.ronan.serialx.api.controller;

import com.ronan.serialx.common.response.ApiResponse;
import com.ronan.serialx.api.dto.IdBatchGenerateRequest;
import com.ronan.serialx.api.dto.IdBatchGenerateResponse;
import com.ronan.serialx.api.dto.IdGenerateRequest;
import com.ronan.serialx.api.dto.IdGenerateResponse;
import com.ronan.serialx.api.dto.NamespaceRuntimeStatusResponse;
import com.ronan.serialx.application.generate.IdGenerateManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ID 生成接口。
 */
@RestController
@RequestMapping("/api/ids")
@RequiredArgsConstructor
public class IdGenerateController {

    private final IdGenerateManager idGenerateManager;

    /**
     * 生成单个 ID。
     */
    @PostMapping("/next")
    public ApiResponse<IdGenerateResponse> nextId(@Valid @RequestBody IdGenerateRequest request) {
        return ApiResponse.success(idGenerateManager.nextId(request.getNamespaceCode()));
    }

    /**
     * 批量生成 ID。
     */
    @PostMapping("/batch")
    public ApiResponse<IdBatchGenerateResponse> batchNextId(@Valid @RequestBody IdBatchGenerateRequest request) {
        return ApiResponse.success(idGenerateManager.batchNextId(request.getNamespaceCode(), request.getCount()));
    }

    /**
     * 查询 Namespace 运行时状态。
     */
    @GetMapping("/runtime/{namespaceCode}")
    public ApiResponse<NamespaceRuntimeStatusResponse> runtimeStatus(@PathVariable String namespaceCode) {
        return ApiResponse.success(idGenerateManager.getRuntimeStatus(namespaceCode));
    }
}
