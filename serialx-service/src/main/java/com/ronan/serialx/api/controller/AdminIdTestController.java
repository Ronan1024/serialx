package com.ronan.serialx.api.controller;

import com.ronan.serialx.api.dto.IdBatchGenerateRequest;
import com.ronan.serialx.api.dto.IdBatchGenerateResponse;
import com.ronan.serialx.api.dto.IdGenerateRequest;
import com.ronan.serialx.api.dto.IdGenerateResponse;
import com.ronan.serialx.application.generate.IdGenerateManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理台发号测试接口。
 */
@RestController
@RequestMapping("/api/admin/ids/test")
@RequiredArgsConstructor
public class AdminIdTestController {

    private final IdGenerateManager idGenerateManager;

    /**
     * 测试生成单个 ID。
     */
    @PostMapping("/next")
    public IdGenerateResponse nextId(@Valid @RequestBody IdGenerateRequest request) {
        return idGenerateManager.nextId(request.getNamespaceCode());
    }

    /**
     * 测试批量生成 ID。
     */
    @PostMapping("/batch")
    public IdBatchGenerateResponse batchNextId(@Valid @RequestBody IdBatchGenerateRequest request) {
        return idGenerateManager.batchNextId(request.getNamespaceCode(), request.getCount());
    }
}
