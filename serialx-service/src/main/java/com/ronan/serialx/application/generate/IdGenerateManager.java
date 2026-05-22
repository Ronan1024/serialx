package com.ronan.serialx.application.generate;

import java.util.ArrayList;
import java.util.List;

import com.ronan.serialx.api.dto.IdBatchGenerateResponse;
import com.ronan.serialx.api.dto.IdGenerateResponse;
import com.ronan.serialx.api.dto.NamespaceRuntimeStatusResponse;
import com.ronan.serialx.application.stats.CallStatsRecord;
import com.ronan.serialx.application.stats.CallStatsRecorder;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import com.ronan.serialx.support.ClientIdentity;
import com.ronan.serialx.support.ClientIdentityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ID 生成业务编排。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdGenerateManager {

    private static final int MIN_BATCH_COUNT = 1;

    private static final int MAX_BATCH_COUNT = 1000;

    private final NamespaceRegistry namespaceRegistry;

    private final CallStatsRecorder callStatsRecorder;

    /**
     * 生成单个 ID。
     */
    public IdGenerateResponse nextId(String namespaceCode) {
        long startNanos = System.nanoTime();
        try {
            NamespaceRuntime runtime = getRuntime(namespaceCode);
            IdGenerateResponse response = IdGenerateResponse.builder()
                    .namespaceCode(namespaceCode)
                    .version(runtime.getVersion())
                    .id(String.valueOf(runtime.nextId()))
                    .build();
            recordStats(namespaceCode, "next", true, null, 1, startNanos);
            return response;
        } catch (RuntimeException ex) {
            recordStats(namespaceCode, "next", false, errorCode(ex), 0, startNanos);
            throw ex;
        }
    }

    /**
     * 批量生成 ID。
     */
    public IdBatchGenerateResponse batchNextId(String namespaceCode, int count) {
        long startNanos = System.nanoTime();
        try {
            if (count < MIN_BATCH_COUNT || count > MAX_BATCH_COUNT) {
                throw new BizException(BusinessErrorCode.ID_GENERATE_PARAM_INVALID);
            }
            NamespaceRuntime runtime = getRuntime(namespaceCode);
            List<String> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ids.add(String.valueOf(runtime.nextId()));
            }
            IdBatchGenerateResponse response = IdBatchGenerateResponse.builder()
                    .namespaceCode(namespaceCode)
                    .version(runtime.getVersion())
                    .ids(ids)
                    .build();
            recordStats(namespaceCode, "batch", true, null, count, startNanos);
            return response;
        } catch (RuntimeException ex) {
            recordStats(namespaceCode, "batch", false, errorCode(ex), 0, startNanos);
            throw ex;
        }
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
        return namespaceRegistry.get(namespaceCode).orElseThrow(() -> new BizException(BusinessErrorCode.ID_NAMESPACE_NOT_READY));
    }

    private void recordStats(
            String namespaceCode,
            String operation,
            boolean success,
            String errorCode,
            int generatedCount,
            long startNanos) {
        try {
            ClientIdentity identity = ClientIdentityContext.get().orElse(null);
            callStatsRecorder.record(CallStatsRecord.builder()
                    .namespaceCode(namespaceCode)
                    .clientName(identity == null ? null : identity.getClientName())
                    .clientId(identity == null ? null : identity.getClientId())
                    .operation(operation)
                    .success(success)
                    .errorCode(errorCode)
                    .generatedCount(generatedCount)
                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000)
                    .build());
        } catch (RuntimeException ex) {
            log.debug("record id generate call stats failed, namespaceCode={}", namespaceCode, ex);
        }
    }

    private String errorCode(RuntimeException ex) {
        if (ex instanceof BizException bizException) {
            return bizException.getCode();
        }
        return ex.getClass().getSimpleName();
    }
}
