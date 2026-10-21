package com.ronan.serialx.admin.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ronan.serialx.admin.dto.NamespaceCreateRequest;
import com.ronan.serialx.admin.dto.NamespacePageQueryRequest;
import com.ronan.serialx.admin.dto.NamespaceVersionResponse;
import com.ronan.serialx.admin.convert.NamespaceConvert;
import com.ronan.serialx.admin.manager.support.NamespaceRequestValidator;
import com.ronan.serialx.admin.manager.support.config.NamespaceConfigHandlerRegistry;
import com.ronan.serialx.admin.manager.support.config.SegmentNamespaceConfigHandler;
import com.ronan.serialx.admin.manager.support.config.SnowflakeNamespaceConfigHandler;
import com.ronan.serialx.admin.manager.support.NamespaceSnapshotSupport;
import com.ronan.serialx.admin.service.NamespaceChangeLogService;
import com.ronan.serialx.admin.service.NamespaceConfigService;
import com.ronan.serialx.admin.service.NamespaceService;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.common.enums.NamespaceStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.entity.NamespaceDO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * NamespaceManager 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class NamespaceManagerTest {

    @Mock
    private NamespaceService namespaceService;

    @Mock
    private NamespaceConfigService namespaceConfigService;

    @Mock
    private NamespaceChangeLogService namespaceChangeLogService;

    @Mock
    private ServiceInstanceConfigStatusService serviceInstanceConfigStatusService;

    @Mock
    private NamespaceChangePublisher namespaceChangePublisher;

    private NamespaceManager namespaceManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NamespaceConvert namespaceConvert = Mappers.getMapper(NamespaceConvert.class);

    private NamespaceConfigHandlerRegistry namespaceConfigHandlerRegistry;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        namespaceConfigHandlerRegistry = new NamespaceConfigHandlerRegistry(List.of(
                new SegmentNamespaceConfigHandler(objectMapper),
                new SnowflakeNamespaceConfigHandler(objectMapper)));
        namespaceManager = new NamespaceManager(
                namespaceService,
                namespaceConfigService,
                namespaceChangeLogService,
                serviceInstanceConfigStatusService,
                namespaceChangePublisher,
                new NamespaceRequestValidator(),
                new NamespaceSnapshotSupport(objectMapper),
                namespaceConfigHandlerRegistry);
    }

    @Test
    void shouldRejectInvalidNamespaceCodeWhenCreateNamespace() throws Exception {
        NamespaceCreateRequest request = new NamespaceCreateRequest();
        request.setNamespaceCode("1-invalid");
        request.setNamespaceName("订单发号");
        request.setIdMode(1);
        request.setOwner("tester");
        request.setConfig(objectMapper.readTree("""
                {"stepSize":100,"initialValue":0,"cacheSegments":1}
                """));

        assertThrows(BizException.class, () -> namespaceManager.createNamespace(request));
        verify(namespaceService, never()).create(any());
    }

    @Test
    void shouldCreateDraftNamespaceWhenRequestIsValid() throws Exception {
        NamespaceCreateRequest request = new NamespaceCreateRequest();
        request.setNamespaceCode("order_id");
        request.setNamespaceName("订单发号");
        request.setIdMode(1);
        request.setOwner("tester");
        request.setRemark("remark");
        request.setConfig(objectMapper.readTree("""
                {"stepSize":100,"initialValue":0,"cacheSegments":1}
                """));

        when(namespaceService.existsByCode("order_id")).thenReturn(false);
        when(namespaceConfigService.getEditingConfig(1L)).thenAnswer(invocation -> {
            NamespaceConfigDO configDO = new NamespaceConfigDO();
            configDO.setNamespaceId(1L);
            configDO.setVersion(0);
            configDO.setIdMode(1);
            configDO.setConfigJson("{\"stepSize\":100,\"initialValue\":0,\"cacheSegments\":1}");
            return configDO;
        });
        when(namespaceService.getById(1L)).thenAnswer(invocation -> {
            NamespaceDO namespaceDO = new NamespaceDO();
            namespaceDO.setId(1L);
            namespaceDO.setNamespaceCode("order_id");
            namespaceDO.setNamespaceName("订单发号");
            namespaceDO.setIdMode(1);
            namespaceDO.setStatus(NamespaceStatusEnum.DRAFT.getCode());
            namespaceDO.setOwner("tester");
            namespaceDO.setRemark("remark");
            namespaceDO.setCurrentVersion(0);
            return namespaceDO;
        });

        when(namespaceService.create(any())).thenAnswer(invocation -> {
            NamespaceDO namespaceDO = invocation.getArgument(0);
            namespaceDO.setId(1L);
            return namespaceDO;
        });

        assertEquals("order_id", namespaceManager.createNamespace(request).getNamespaceCode());
        verify(namespaceService).create(any());
        verify(namespaceConfigService).create(any());
        verify(namespaceChangeLogService).create(any());
    }

    @Test
    void shouldAllowSnowflakeConfigWithoutWorkerAndDatacenterIds() throws Exception {
        NamespaceCreateRequest request = new NamespaceCreateRequest();
        request.setNamespaceCode("payment_id");
        request.setNamespaceName("支付发号");
        request.setIdMode(2);
        request.setOwner("tester");
        request.setConfig(null);

        when(namespaceService.existsByCode("payment_id")).thenReturn(false);
        when(namespaceConfigService.getEditingConfig(2L)).thenAnswer(invocation -> {
            NamespaceConfigDO configDO = new NamespaceConfigDO();
            configDO.setNamespaceId(2L);
            configDO.setVersion(0);
            configDO.setIdMode(2);
            configDO.setConfigJson("{}");
            return configDO;
        });
        when(namespaceService.getById(2L)).thenAnswer(invocation -> {
            NamespaceDO namespaceDO = new NamespaceDO();
            namespaceDO.setId(2L);
            namespaceDO.setNamespaceCode("payment_id");
            namespaceDO.setNamespaceName("支付发号");
            namespaceDO.setIdMode(2);
            namespaceDO.setStatus(NamespaceStatusEnum.DRAFT.getCode());
            namespaceDO.setOwner("tester");
            namespaceDO.setCurrentVersion(0);
            return namespaceDO;
        });
        when(namespaceService.create(any())).thenAnswer(invocation -> {
            NamespaceDO namespaceDO = invocation.getArgument(0);
            namespaceDO.setId(2L);
            return namespaceDO;
        });

        assertEquals("payment_id", namespaceManager.createNamespace(request).getNamespaceCode());
        verify(namespaceService).create(any());
        verify(namespaceConfigService).create(argThat(config ->
                "{}".equals(config.getConfigJson()) && Integer.valueOf(2).equals(config.getIdMode())));
    }

    @Test
    void shouldListPublishedVersions() {
        NamespaceDO namespaceDO = new NamespaceDO();
        namespaceDO.setId(1L);
        namespaceDO.setNamespaceCode("order_id");
        namespaceDO.setNamespaceName("订单发号");
        namespaceDO.setIdMode(1);
        namespaceDO.setStatus(NamespaceStatusEnum.ENABLED.getCode());
        namespaceDO.setOwner("tester");
        namespaceDO.setCurrentVersion(2);

        NamespaceConfigDO configDO = new NamespaceConfigDO();
        configDO.setNamespaceId(1L);
        configDO.setVersion(2);
        configDO.setIdMode(1);
        configDO.setConfigJson("{\"stepSize\":100,\"initialValue\":0,\"cacheSegments\":1}");
        configDO.setConfigChecksum("checksum");
        configDO.setPublishedBy("admin");

        when(namespaceService.getById(1L)).thenReturn(namespaceDO);
        when(namespaceConfigService.listPublishedConfigs(1L)).thenReturn(List.of(configDO));

        List<NamespaceVersionResponse> versions = namespaceManager.listPublishedVersions(1L);

        assertEquals(1, versions.size());
        assertEquals(2, versions.getFirst().getVersion());
        assertEquals("admin", versions.getFirst().getOperator());
        assertEquals(100, versions.getFirst().getConfig().path("stepSize").asInt());
    }

    @Test
    void shouldPublishDraftAndEnableNamespace() {
        NamespaceDO namespace = namespace(0, NamespaceStatusEnum.DRAFT);
        when(namespaceService.getById(1L)).thenReturn(namespace);
        when(namespaceConfigService.getEditingConfig(1L)).thenReturn(config(0));
        when(namespaceService.updateVersionAndStatus(1L, 0, 0, 1, 1)).thenAnswer(invocation -> {
            namespace.setCurrentVersion(1);
            namespace.setStatus(1);
            return true;
        });

        assertEquals(1, namespaceManager.publishNamespace(1L).getCurrentVersion());
        verify(namespaceConfigService).create(argThat(value -> value.getVersion() == 1));
        verify(namespaceChangePublisher).publish(argThat(message -> "PUBLISH".equals(message.getEventType())));
    }

    @Test
    void shouldRejectPublishingWithoutDraft() {
        when(namespaceService.getById(1L)).thenReturn(namespace(0, NamespaceStatusEnum.DRAFT));
        assertBusinessError(BusinessErrorCode.NAMESPACE_CONFIG_INVALID, () -> namespaceManager.publishNamespace(1L));
        verify(namespaceConfigService, never()).create(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENABLE", "DISABLE", "ROLLBACK", "DELETE"})
    void shouldApplyStateChangeAndNotify(String action) {
        NamespaceDO namespace = prepareOperation(action);
        switch (action) {
            case "ENABLE" -> when(namespaceService.updateStatus(1L, 2, 2, 1)).thenReturn(true);
            case "DISABLE" -> when(namespaceService.updateStatus(1L, 2, 1, 2)).thenReturn(true);
            case "ROLLBACK" -> when(namespaceService.updateCurrentVersion(1L, 2, 1, 1)).thenReturn(true);
            case "DELETE" -> when(namespaceService.markDeleted(1L, 2, 2)).thenReturn(true);
            default -> throw new IllegalArgumentException(action);
        }
        invokeOperation(action);
        ArgumentCaptor<com.ronan.serialx.infra.entity.NamespaceChangeLogDO> log =
                ArgumentCaptor.forClass(com.ronan.serialx.infra.entity.NamespaceChangeLogDO.class);
        verify(namespaceChangeLogService).create(log.capture());
        assertEquals(namespace.getId(), log.getValue().getNamespaceId());
        verify(namespaceChangePublisher).publish(argThat(message -> action.equals(message.getEventType())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISH", "ENABLE", "DISABLE", "ROLLBACK", "DELETE"})
    void shouldRejectConcurrentStateChangeWithoutAuditOrNotification(String action) {
        prepareOperation(action);
        BusinessErrorCode expected = switch (action) {
            case "PUBLISH" -> BusinessErrorCode.NAMESPACE_PUBLISH_FAILED;
            case "ROLLBACK" -> BusinessErrorCode.NAMESPACE_ROLLBACK_FAILED;
            default -> BusinessErrorCode.NAMESPACE_STATUS_INVALID;
        };
        assertBusinessError(expected, () -> invokeOperation(action));
        verify(namespaceChangeLogService, never()).create(any());
        verify(namespaceChangePublisher, never()).publish(any());
    }

    @Test
    void shouldRejectEnableWithoutPublishedVersion() {
        when(namespaceService.getById(1L)).thenReturn(namespace(0, NamespaceStatusEnum.DRAFT));
        assertBusinessError(BusinessErrorCode.NAMESPACE_STATUS_INVALID, () -> namespaceManager.enableNamespace(1L));
        verify(namespaceService, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void shouldRejectDeletingEnabledNamespace() {
        when(namespaceService.getById(1L)).thenReturn(namespace(2, NamespaceStatusEnum.ENABLED));
        assertBusinessError(BusinessErrorCode.NAMESPACE_DELETE_FORBIDDEN, () -> namespaceManager.deleteNamespace(1L));
        verify(namespaceService, never()).markDeleted(any(), any(), any());
    }

    @Test
    void shouldRejectMissingRollbackVersion() {
        when(namespaceService.getById(1L)).thenReturn(namespace(2, NamespaceStatusEnum.ENABLED));
        assertBusinessError(BusinessErrorCode.NAMESPACE_VERSION_NOT_FOUND, () -> namespaceManager.rollbackNamespace(1L, 99));
        verify(namespaceService, never()).updateCurrentVersion(any(), any(), any(), any());
    }

    @Test
    void shouldRejectMissingNamespace() {
        assertBusinessError(BusinessErrorCode.NAMESPACE_NOT_FOUND, () -> namespaceManager.getNamespace(99L));
    }

    @Test
    void shouldAllowValidPageFilters() {
        when(namespaceService.pageNamespaces(1, 20, null, 1, 2)).thenReturn(Page.of(1, 20));
        assertEquals(0, namespaceManager.pageNamespaces(pageRequest(1, 2)).getTotal());
        verify(namespaceService).pageNamespaces(1, 20, null, 1, 2);
    }

    @Test
    void shouldForwardFilterCodesToExactMatchQuery() {
        when(namespaceService.pageNamespaces(1, 20, null, 99, 99)).thenReturn(Page.of(1, 20));
        assertEquals(0, namespaceManager.pageNamespaces(pageRequest(99, 99)).getTotal());
        verify(namespaceService).pageNamespaces(1, 20, null, 99, 99);
    }

    @Test
    void shouldBatchLoadConfigsAndKeepPageOrderAndVersions() {
        NamespaceDO first = namespace(2, NamespaceStatusEnum.ENABLED);
        NamespaceDO second = namespace(3, NamespaceStatusEnum.ENABLED);
        second.setId(2L);
        NamespaceDO third = namespace(0, NamespaceStatusEnum.DRAFT);
        third.setId(3L);
        Page<NamespaceDO> page = Page.of(1, 20);
        page.setTotal(27);
        page.setRecords(List.of(second, first, third));
        when(namespaceService.pageNamespaces(1, 20, null, null, null)).thenReturn(page);
        when(namespaceConfigService.listEditingConfigs(List.of(2L, 1L, 3L))).thenReturn(List.of(
                responseConfig(20L, 2L, 0, "{\"stepSize\":202}"),
                responseConfig(10L, 1L, 0, "{\"stepSize\":101}"),
                responseConfig(9L, 1L, 0, "{\"stepSize\":11}")));
        when(namespaceConfigService.listPublishedConfigsByVersions(Map.of(1L, 2, 2L, 3))).thenReturn(List.of(
                responseConfig(30L, 1L, 2, "{\"stepSize\":1000}"),
                responseConfig(40L, 2L, 3, "{\"stepSize\":2000}")));

        var result = namespaceManager.pageNamespaces(pageRequest(null, null));

        assertEquals(27, result.getTotal());
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
        assertEquals(List.of(2L, 1L, 3L), result.getRecords().stream().map(value -> value.getId()).toList());
        assertEquals(202, result.getRecords().getFirst().getDraftConfig().path("stepSize").asInt());
        assertEquals(101, result.getRecords().get(1).getDraftConfig().path("stepSize").asInt());
        assertEquals(2000, result.getRecords().getFirst().getPublishedConfig().path("stepSize").asInt());
        assertEquals(1000, result.getRecords().get(1).getPublishedConfig().path("stepSize").asInt());
        assertNull(result.getRecords().get(2).getDraftConfig());
        assertNull(result.getRecords().get(2).getPublishedConfig());
        verify(namespaceConfigService).listEditingConfigs(List.of(2L, 1L, 3L));
        verify(namespaceConfigService).listPublishedConfigsByVersions(Map.of(1L, 2, 2L, 3));
        verifyNoMoreInteractions(namespaceConfigService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void shouldUseTwoConfigQueriesRegardlessOfPageSize(int count) {
        List<NamespaceDO> namespaces = java.util.stream.LongStream.rangeClosed(1, count).mapToObj(id -> {
            NamespaceDO value = namespace(1, NamespaceStatusEnum.ENABLED);
            value.setId(id);
            return value;
        }).toList();
        Page<NamespaceDO> page = Page.of(1, count);
        page.setTotal(count);
        page.setRecords(namespaces);
        var request = pageRequest(null, null);
        request.setPageSize((long) count);
        when(namespaceService.pageNamespaces(1, count, null, null, null)).thenReturn(page);

        assertEquals(count, namespaceManager.pageNamespaces(request).getRecords().size());

        verify(namespaceConfigService).listEditingConfigs(namespaces.stream().map(NamespaceDO::getId).toList());
        verify(namespaceConfigService).listPublishedConfigsByVersions(namespaces.stream()
                .collect(java.util.stream.Collectors.toMap(NamespaceDO::getId, NamespaceDO::getCurrentVersion)));
        verifyNoMoreInteractions(namespaceConfigService);
    }

    @Test
    void shouldSkipPublishedQueryWhenNoNamespaceHasPublishedVersion() {
        Page<NamespaceDO> page = Page.of(1, 20);
        page.setRecords(List.of(namespace(0, NamespaceStatusEnum.DRAFT)));
        when(namespaceService.pageNamespaces(1, 20, null, null, null)).thenReturn(page);

        assertNull(namespaceManager.pageNamespaces(pageRequest(null, null)).getRecords().getFirst().getPublishedConfig());

        verify(namespaceConfigService).listEditingConfigs(List.of(1L));
        verifyNoMoreInteractions(namespaceConfigService);
    }

    @Test
    void shouldSkipConfigQueriesForEmptyPageWithoutChangingTotal() {
        Page<NamespaceDO> page = Page.of(1, 20);
        page.setTotal(27);
        when(namespaceService.pageNamespaces(1, 20, null, null, null)).thenReturn(page);

        var result = namespaceManager.pageNamespaces(pageRequest(null, null));

        assertEquals(27, result.getTotal());
        assertEquals(List.of(), result.getRecords());
        verifyNoInteractions(namespaceConfigService);
    }

    @Test
    void shouldKeepMissingPublishedConfigNullWithoutUsingDraft() {
        Page<NamespaceDO> page = Page.of(1, 20);
        page.setRecords(List.of(namespace(1, NamespaceStatusEnum.ENABLED)));
        when(namespaceService.pageNamespaces(1, 20, null, null, null)).thenReturn(page);
        when(namespaceConfigService.listEditingConfigs(List.of(1L)))
                .thenReturn(List.of(responseConfig(1L, 1L, 0, "{}")));

        var result = namespaceManager.pageNamespaces(pageRequest(null, null)).getRecords().getFirst();

        assertEquals(objectMapper.createObjectNode(), result.getDraftConfig());
        assertNull(result.getPublishedConfig());
        verify(namespaceConfigService).listPublishedConfigsByVersions(Map.of(1L, 1));
    }

    @Test
    void shouldRejectInvalidJsonInBatchResponse() {
        Page<NamespaceDO> page = Page.of(1, 20);
        page.setRecords(List.of(namespace(0, NamespaceStatusEnum.DRAFT)));
        when(namespaceService.pageNamespaces(1, 20, null, null, null)).thenReturn(page);
        when(namespaceConfigService.listEditingConfigs(List.of(1L)))
                .thenReturn(List.of(responseConfig(1L, 1L, 0, "{invalid")));

        assertBusinessError(BusinessErrorCode.NAMESPACE_CONFIG_INVALID,
                () -> namespaceManager.pageNamespaces(pageRequest(null, null)));
    }

    @Test
    void shouldKeepSingleDetailConfigLookupSemantics() {
        when(namespaceService.getById(1L)).thenReturn(namespace(1, NamespaceStatusEnum.ENABLED));
        when(namespaceConfigService.getEditingConfig(1L)).thenReturn(config(0));
        when(namespaceConfigService.getPublishedConfig(1L, 1)).thenReturn(config(1));

        var result = namespaceManager.getNamespace(1L);

        assertEquals(1, result.getCurrentVersion());
        assertEquals(objectMapper.createObjectNode(), result.getDraftConfig());
        assertEquals(objectMapper.createObjectNode(), result.getPublishedConfig());
        verify(namespaceConfigService).getEditingConfig(1L);
        verify(namespaceConfigService).getPublishedConfig(1L, 1);
        verifyNoMoreInteractions(namespaceConfigService);
    }

    @Test
    void shouldNotifyOnlyAfterCommit() {
        prepareOperation("DISABLE");
        when(namespaceService.updateStatus(1L, 2, 1, 2)).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            namespaceManager.disableNamespace(1L);
            verify(namespaceChangePublisher, never()).publish(any());
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(namespaceChangePublisher).publish(argThat(message -> "DISABLE".equals(message.getEventType())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldPublishVersionFourAfterRollingBackFromThreeToOne() {
        NamespaceDO namespace = namespace(3, NamespaceStatusEnum.ENABLED);
        when(namespaceService.getById(1L)).thenReturn(namespace);
        when(namespaceConfigService.getPublishedConfig(1L, 1)).thenReturn(config(1));
        when(namespaceService.updateCurrentVersion(1L, 3, 1, 1)).thenAnswer(invocation -> {
            namespace.setCurrentVersion(1);
            return true;
        });
        assertEquals(1, namespaceManager.rollbackNamespace(1L, 1).getCurrentVersion());

        when(namespaceConfigService.getEditingConfig(1L)).thenReturn(config(0));
        when(namespaceConfigService.getMaxPublishedVersion(1L)).thenReturn(3);
        when(namespaceConfigService.create(any())).thenAnswer(invocation -> {
            NamespaceConfigDO value = invocation.getArgument(0);
            if (value.getVersion() <= 3) {
                throw new DuplicateKeyException("historical version exists");
            }
            return value;
        });
        when(namespaceService.updateVersionAndStatus(1L, 1, 1, 4, 1)).thenAnswer(invocation -> {
            namespace.setCurrentVersion(4);
            return true;
        });

        assertEquals(4, namespaceManager.publishNamespace(1L).getCurrentVersion());
        verify(namespaceConfigService).create(argThat(value -> value.getVersion() == 4));
        verify(namespaceChangePublisher).publish(argThat(message ->
                "PUBLISH".equals(message.getEventType()) && message.getVersion() == 4));
    }

    @Test
    void shouldRejectDuplicatePublishedVersionWithoutNotification() {
        prepareOperation("PUBLISH");
        when(namespaceConfigService.create(any())).thenThrow(new DuplicateKeyException("publish conflict"));
        assertBusinessError(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED, () -> namespaceManager.publishNamespace(1L));
        verify(namespaceService, never()).updateVersionAndStatus(any(), any(), any(), any(), any());
        verify(namespaceChangeLogService, never()).create(any());
        verify(namespaceChangePublisher, never()).publish(any());
    }

    @Test
    void shouldRejectVersionOverflowBeforeWritingConfig() {
        prepareOperation("PUBLISH");
        when(namespaceConfigService.getMaxPublishedVersion(1L)).thenReturn(Integer.MAX_VALUE);
        assertBusinessError(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED, () -> namespaceManager.publishNamespace(1L));
        verify(namespaceConfigService, never()).create(any());
        verify(namespaceChangePublisher, never()).publish(any());
    }

    private NamespacePageQueryRequest pageRequest(Integer status, Integer idMode) {
        NamespacePageQueryRequest request = new NamespacePageQueryRequest();
        request.setStatus(status);
        request.setIdMode(idMode);
        return request;
    }

    private NamespaceConfigDO responseConfig(Long id, Long namespaceId, int version, String json) {
        return NamespaceConfigDO.builder().id(id).namespaceId(namespaceId).version(version)
                .configJson(json).publishStatus(version == 0 ? 0 : 1).build();
    }

    private NamespaceDO prepareOperation(String action) {
        NamespaceDO namespace = namespace(2,
                "ENABLE".equals(action) || "DELETE".equals(action)
                        ? NamespaceStatusEnum.DISABLED : NamespaceStatusEnum.ENABLED);
        when(namespaceService.getById(1L)).thenReturn(namespace);
        if ("PUBLISH".equals(action)) {
            when(namespaceConfigService.getEditingConfig(1L)).thenReturn(config(0));
            when(namespaceConfigService.getMaxPublishedVersion(1L)).thenReturn(2);
        }
        if ("ROLLBACK".equals(action)) {
            when(namespaceConfigService.getPublishedConfig(1L, 1)).thenReturn(config(1));
        }
        return namespace;
    }

    private void invokeOperation(String action) {
        switch (action) {
            case "PUBLISH" -> namespaceManager.publishNamespace(1L);
            case "ENABLE" -> namespaceManager.enableNamespace(1L);
            case "DISABLE" -> namespaceManager.disableNamespace(1L);
            case "ROLLBACK" -> namespaceManager.rollbackNamespace(1L, 1);
            case "DELETE" -> namespaceManager.deleteNamespace(1L);
            default -> throw new IllegalArgumentException(action);
        }
    }

    private NamespaceDO namespace(int version, NamespaceStatusEnum status) {
        NamespaceDO value = new NamespaceDO();
        value.setId(1L);
        value.setNamespaceCode("order_id");
        value.setNamespaceName("订单发号");
        value.setIdMode(2);
        value.setCurrentVersion(version);
        value.setStatus(status.getCode());
        return value;
    }

    private NamespaceConfigDO config(int version) {
        return NamespaceConfigDO.builder().namespaceId(1L).version(version).idMode(2)
                .configJson("{}").publishStatus(version == 0 ? 0 : 1).build();
    }

    private void assertBusinessError(BusinessErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected.getCode(), assertThrows(BizException.class, action).getCode());
    }
}
