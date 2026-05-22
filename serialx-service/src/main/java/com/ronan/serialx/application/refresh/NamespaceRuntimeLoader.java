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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
     * 应用启动后执行一次 Namespace 全量加载。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnApplicationReady() {
        loadPublishedNamespaces();
    }

    /**
     * 启动或定时全量加载已启用、已发布 Namespace。
     */
    public void loadPublishedNamespaces() {
        List<NamespaceDO> namespaces = namespaceMapper.selectList(new LambdaQueryWrapper<NamespaceDO>()
                .eq(NamespaceDO::getDeleted, NOT_DELETED)
                .eq(NamespaceDO::getStatus, NamespaceStatusEnum.ENABLED.getCode())
                .gt(NamespaceDO::getCurrentVersion, MIN_PUBLISHED_VERSION));
        Set<String> enabledCodes = namespaces.stream()
                .map(NamespaceDO::getNamespaceCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        namespaces.forEach(namespace -> refresh(namespace, namespace.getCurrentVersion()));
        namespaceRegistry.listAll().stream()
                .map(NamespaceRuntime::getNamespaceCode)
                .filter(namespaceCode -> !enabledCodes.contains(namespaceCode))
                .toList()
                .forEach(namespaceCode -> remove(namespaceCode, ServiceInstanceRedisConstants.OFFLINE));
    }

    /**
     * 刷新单个 Namespace。
     */
    public void refresh(String namespaceCode, Integer version) {
        if (!StringUtils.hasText(namespaceCode)) {
            return;
        }
        NamespaceDO namespace = selectFirst(namespaceMapper, new LambdaQueryWrapper<NamespaceDO>()
                .eq(NamespaceDO::getNamespaceCode, namespaceCode)
                .eq(NamespaceDO::getDeleted, NOT_DELETED)
                .orderByDesc(NamespaceDO::getId));
        if (namespace == null || !Objects.equals(NamespaceStatusEnum.ENABLED.getCode(), namespace.getStatus())) {
            remove(namespaceCode, ServiceInstanceRedisConstants.DISABLED);
            return;
        }
        Integer targetVersion = version == null ? namespace.getCurrentVersion() : version;
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
            log.warn("published namespace config not found, namespaceCode={}, version={}", namespaceCode, targetVersion);
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
            reportNamespaceStatus(namespaceCode, config.getVersion(), ServiceInstanceRedisConstants.ACTIVE);
        } catch (Exception ex) {
            reportNamespaceStatus(namespaceCode, targetVersion, ServiceInstanceRedisConstants.LOAD_FAILED);
            log.warn("load namespace runtime failed, namespaceCode={}, version={}", namespaceCode, targetVersion, ex);
        }
    }

    /**
     * 移除本地 Namespace 运行时。
     */
    public void remove(String namespaceCode, String runtimeStatus) {
        if (!StringUtils.hasText(namespaceCode)) {
            return;
        }
        namespaceRegistry.remove(namespaceCode);
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
        statusWriter.saveOrUpdate(status);
    }
}
