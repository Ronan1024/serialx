package com.ronan.serialx.application.generate;

import java.util.ArrayList;
import java.util.List;

import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.api.dto.IdBatchGenerateResponse;
import com.ronan.serialx.api.dto.IdGenerateResponse;
import com.ronan.serialx.api.dto.NamespaceRuntimeStatusResponse;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ID 生成业务编排。
 */
@Component
@RequiredArgsConstructor
public class IdGenerateManager {

    private static final int MIN_BATCH_COUNT = 1;

    private static final int MAX_BATCH_COUNT = 1000;

    private final NamespaceRegistry namespaceRegistry;

    /**
     * 生成单个 ID。
     */
    public IdGenerateResponse nextId(String namespaceCode) {
        NamespaceRuntime runtime = getRuntime(namespaceCode);
        return IdGenerateResponse.builder()
                .namespaceCode(namespaceCode)
                .version(runtime.getVersion())
                .id(String.valueOf(runtime.nextId()))
                .build();
    }

    /**
     * 批量生成 ID。
     */
    public IdBatchGenerateResponse batchNextId(String namespaceCode, int count) {
        if (count < MIN_BATCH_COUNT || count > MAX_BATCH_COUNT) {
            throw new BizException(BusinessErrorCode.ID_GENERATE_PARAM_INVALID);
        }
        NamespaceRuntime runtime = getRuntime(namespaceCode);
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(String.valueOf(runtime.nextId()));
        }
        return IdBatchGenerateResponse.builder()
                .namespaceCode(namespaceCode)
                .version(runtime.getVersion())
                .ids(ids)
                .build();
    }

    /**
     * 查询 Namespace 运行时状态。
     */
    public NamespaceRuntimeStatusResponse getRuntimeStatus(String namespaceCode) {
        NamespaceRuntime runtime = getRuntime(namespaceCode);
        return NamespaceRuntimeStatusResponse.builder()
                .namespaceCode(namespaceCode)
                .version(runtime.getVersion())
                .idMode(runtime.getIdMode())
                .runtimeStatus(ServiceInstanceRedisConstants.ACTIVE)
                .build();
    }

    private NamespaceRuntime getRuntime(String namespaceCode) {
        return namespaceRegistry.get(namespaceCode)
                .orElseThrow(() -> new BizException(BusinessErrorCode.ID_NAMESPACE_NOT_READY));
    }
}
