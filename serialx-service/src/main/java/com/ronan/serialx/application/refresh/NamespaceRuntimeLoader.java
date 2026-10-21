package com.ronan.serialx.application.refresh;

import static com.ronan.serialx.infra.support.MybatisQueryUtils.selectFirst;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.adapter.redis.ServiceInstanceStatusWriterImpl;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.common.enums.NamespacePublishStatusEnum;
import com.ronan.serialx.common.enums.NamespaceStatusEnum;
import com.ronan.serialx.config.ServiceInstanceProperties;
import com.ronan.serialx.domain.factory.NamespaceRuntimeFactory;
import com.ronan.serialx.domain.model.NamespaceRuntimeSnapshot;
import com.ronan.serialx.domain.registry.NamespaceRegistry;
import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import com.ronan.serialx.infra.mapper.NamespaceConfigMapper;
import com.ronan.serialx.infra.mapper.NamespaceMapper;
import com.ronan.serialx.support.ServiceInstanceIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Namespace 运行时加载器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NamespaceRuntimeLoader {

    private static final int NOT_DELETED = 0;

    private static final int MIN_PUBLISHED_VERSION = 0;

    private final NamespaceMapper namespaceMapper;

    private final NamespaceConfigMapper namespaceConfigMapper;

    private final NamespaceRuntimeFactory namespaceRuntimeFactory;

    private final NamespaceRegistry namespaceRegistry;

    private final ServiceInstanceStatusWriterImpl statusWriter;

    private final ServiceInstanceProperties serviceInstanceProperties;

    private final ServiceInstanceIdentity serviceInstanceIdentity;

    private final ObjectMapper objectMapper;

    /**
     * 启动或定时全量加载已启用、已发布 Namespace。
     */
    public synchronized void loadPublishedNamespaces() {
        log.debug("Namespace 全量加载开始，runtimeId={}", serviceInstanceIdentity.getRuntimeId());
        List<NamespaceDO> namespaces = namespaceMapper.selectList(new LambdaQueryWrapper<NamespaceDO>()
                .eq(NamespaceDO::getDeleted, NOT_DELETED)
                .eq(NamespaceDO::getStatus, NamespaceStatusEnum.ENABLED.getCode())
                .gt(NamespaceDO::getCurrentVersion, MIN_PUBLISHED_VERSION));
        Set<String> codes = namespaces.stream()
                .map(NamespaceDO::getNamespaceCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        namespaceRegistry.listAll().stream().map(NamespaceRuntime::getNamespaceCode).forEach(codes::add);
        // 全量快照只枚举候选，逐个重查，避免用旧快照移除已经重新启用的运行时。
        codes.forEach(namespaceCode -> refresh(namespaceCode, null));
        log.debug("Namespace 全量加载完成，runtimeId={}, candidateCount={}, loadedCount={}",
                serviceInstanceIdentity.getRuntimeId(), codes.size(), namespaceRegistry.listAll().size());
    }

    /**
     * 刷新单个 Namespace，消息版本仅作提示，以数据库当前生效版本为准。
     */
    public synchronized void refresh(String namespaceCode, Integer version) {
        if (!StringUtils.hasText(namespaceCode)) {
            return;
        }
        log.debug("Namespace 刷新开始，namespaceCode={}, hintedVersion={}", namespaceCode, version);
        NamespaceDO namespace = selectFirst(namespaceMapper, new LambdaQueryWrapper<NamespaceDO>()
                .eq(NamespaceDO::getNamespaceCode, namespaceCode)
                .eq(NamespaceDO::getDeleted, NOT_DELETED)
                .orderByDesc(NamespaceDO::getId));
        if (namespace == null) {
            remove(namespaceCode, ServiceInstanceRedisConstants.OFFLINE);
            return;
        }
        if (!Objects.equals(NamespaceStatusEnum.ENABLED.getCode(), namespace.getStatus())
                || namespace.getCurrentVersion() == null || namespace.getCurrentVersion() <= 0) {
            remove(namespaceCode, ServiceInstanceRedisConstants.DISABLED);
            return;
        }
        Integer targetVersion = namespace.getCurrentVersion();
        NamespaceRuntime existing = namespaceRegistry.get(namespaceCode).orElse(null);
        if (existing != null && Objects.equals(existing.getVersion(), targetVersion)) {
            log.debug("Namespace 版本未变化，namespaceCode={}, version={}", namespaceCode, targetVersion);
            reportNamespaceStatus(namespaceCode, targetVersion, existing.getRuntimeStatus());
            return;
        }
        refresh(namespace, targetVersion);
    }

    private void refresh(NamespaceDO namespace, Integer targetVersion) {
        String namespaceCode = namespace.getNamespaceCode();
        NamespaceConfigDO config = selectFirst(namespaceConfigMapper, new LambdaQueryWrapper<NamespaceConfigDO>()
                .eq(NamespaceConfigDO::getNamespaceId, namespace.getId())
                .eq(NamespaceConfigDO::getVersion, targetVersion)
                .eq(NamespaceConfigDO::getPublishStatus, NamespacePublishStatusEnum.PUBLISHED.getCode())
                .orderByDesc(NamespaceConfigDO::getId));
        if (config == null) {
            reportNamespaceStatus(namespaceCode, targetVersion, ServiceInstanceRedisConstants.LOAD_FAILED);
            log.warn("Namespace 已发布配置缺失，namespaceCode={}, version={}", namespaceCode, targetVersion);
            return;
        }
        try {
            JsonNode configJson = objectMapper.readTree(config.getConfigJson());
            NamespaceRuntime runtime = namespaceRuntimeFactory.create(NamespaceRuntimeSnapshot.builder()
                    .namespaceId(namespace.getId())
                    .namespaceCode(namespace.getNamespaceCode())
                    .version(config.getVersion())
                    .idMode(config.getIdMode())
                    .config(configJson)
                    .build());
            namespaceRegistry.replace(namespaceCode, runtime);
            log.info("Namespace 运行时加载完成，namespaceCode={}, version={}, status={}",
                    namespaceCode, config.getVersion(), runtime.getRuntimeStatus());
            reportNamespaceStatus(namespaceCode, config.getVersion(), runtime.getRuntimeStatus());
        } catch (Exception ex) {
            reportNamespaceStatus(namespaceCode, targetVersion, ServiceInstanceRedisConstants.LOAD_FAILED);
            // 解析异常可能包含配置正文，只记录可关联的元数据和异常类型。
            log.warn("Namespace 运行时加载失败，namespaceCode={}, version={}, exceptionType={}",
                    namespaceCode, targetVersion, ex.getClass().getSimpleName());
        }
    }

    /**
     * 移除本地 Namespace 运行时。
     */
    public synchronized void remove(String namespaceCode, String runtimeStatus) {
        if (!StringUtils.hasText(namespaceCode)) {
            return;
        }
        NamespaceRuntime previous = namespaceRegistry.get(namespaceCode).orElse(null);
        namespaceRegistry.remove(namespaceCode);
        if (previous != null) {
            log.info("Namespace 本地运行时已移除，namespaceCode={}, previousVersion={}, status={}",
                    namespaceCode, previous.getVersion(), runtimeStatus);
        } else {
            log.debug("Namespace 无本地运行时，无需移除，namespaceCode={}, status={}", namespaceCode, runtimeStatus);
        }
        reportNamespaceStatus(namespaceCode, null, runtimeStatus);
    }

    private void reportNamespaceStatus(String namespaceCode, Integer loadedVersion, String runtimeStatus) {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(serviceInstanceIdentity.getInstanceId());
        status.setHost(serviceInstanceIdentity.getHost());
        status.setNamespaceCode(namespaceCode);
        status.setLoadedVersion(loadedVersion);
        status.setRuntimeStatus(runtimeStatus);
        status.setLastReportTime(LocalDateTime.now());
        try {
            statusWriter.saveOrUpdate(status);
            log.debug("Namespace 状态上报完成，runtimeId={}, namespaceCode={}, version={}, status={}",
                    serviceInstanceIdentity.getRuntimeId(), namespaceCode, loadedVersion, runtimeStatus);
        } catch (RuntimeException ex) {
            // Redis 是展示快照，不是节点租约或配置加载的事实来源。
            log.warn("Namespace 状态上报失败，保留本地运行时，runtimeId={}, namespaceCode={}, version={}, exceptionType={}",
                    serviceInstanceIdentity.getRuntimeId(), namespaceCode, loadedVersion, ex.getClass().getSimpleName());
        }
    }
}
