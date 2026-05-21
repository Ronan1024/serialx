package com.ronan.serialx.application.refresh;

import com.ronan.serialx.config.ServiceInstanceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Namespace 全量对齐任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NamespaceFullSyncTask {

    private final ServiceInstanceProperties serviceInstanceProperties;

    private final NamespaceRuntimeLoader namespaceRuntimeLoader;

    /**
     * 定时全量对齐 Namespace 运行时。
     */
    @Scheduled(fixedDelayString = "#{@serviceInstanceProperties.fullSyncInterval.toMillis()}")
    public void fullSync() {
        if (!serviceInstanceProperties.isNamespaceRefreshEnabled()) {
            return;
        }
        try {
            namespaceRuntimeLoader.loadPublishedNamespaces();
        } catch (Exception ex) {
            log.warn("full sync namespace runtime failed", ex);
        }
    }
}
