package com.ronan.serialx.admin.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.ronan.serialx.admin.service.NamespaceService;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.infra.entity.NamespaceDO;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ServiceInstanceNodeManager 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ServiceInstanceNodeManagerTest {

    @Mock
    private ServiceInstanceConfigStatusService serviceInstanceConfigStatusService;

    @Mock
    private NamespaceService namespaceService;

    private ServiceInstanceNodeManager serviceInstanceNodeManager;

    @BeforeEach
    void setUp() {
        serviceInstanceNodeManager = new ServiceInstanceNodeManager(serviceInstanceConfigStatusService, namespaceService);
    }

    @Test
    void shouldGroupNamespaceStatusesByNode() {
        ServiceInstanceConfigStatusDO orderStatus = buildStatus("svc-1", "order_id", 2, "ACTIVE");
        ServiceInstanceConfigStatusDO userStatus = buildStatus("svc-1", "user_id", 1, "ACTIVE");
        NamespaceDO orderNamespace = buildNamespace("order_id", "订单发号", 3);
        NamespaceDO userNamespace = buildNamespace("user_id", "用户发号", 1);
        when(serviceInstanceConfigStatusService.listAll()).thenReturn(List.of(orderStatus, userStatus));
        when(namespaceService.listByCodes(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(orderNamespace, userNamespace));

        var response = serviceInstanceNodeManager.listNodes();

        assertEquals(1, response.getSummary().getTotalCount());
        assertEquals(1, response.getSummary().getOnlineCount());
        assertEquals(1, response.getSummary().getLaggingNamespaceCount());
        assertEquals(1, response.getNodes().size());
        assertEquals(2, response.getNodes().getFirst().getNamespaceCount());
        assertEquals(1, response.getNodes().getFirst().getLaggingNamespaceCount());
        assertTrue(response.getNodes().getFirst().getNamespaces().stream()
                .anyMatch(item -> "order_id".equals(item.getNamespaceCode()) && item.isLagging()));
    }

    @Test
    void shouldReturnOfflineNodeWhenAllStatusesAreOffline() {
        ServiceInstanceConfigStatusDO status = buildStatus("svc-2", "order_id", 2, "OFFLINE");
        status.setLastOfflineTime(LocalDateTime.now());
        NamespaceDO namespace = buildNamespace("order_id", "订单发号", 2);
        when(serviceInstanceConfigStatusService.listAll()).thenReturn(List.of(status));
        when(namespaceService.listByCodes(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(namespace));

        var response = serviceInstanceNodeManager.listNodes();

        assertEquals(1, response.getSummary().getOfflineCount());
        assertEquals("OFFLINE", response.getNodes().getFirst().getRuntimeStatus());
    }

    @Test
    void shouldListSameMachineInstancesSeparately() {
        ServiceInstanceConfigStatusDO offlineStatus = buildStatus("serialx-service-8081", "order_id", 1, "OFFLINE");
        offlineStatus.setHost("serialx-service/127.0.0.1:8081");
        offlineStatus.setLastOfflineTime(LocalDateTime.now());
        ServiceInstanceConfigStatusDO activeStatus = buildStatus("serialx-8080", "user_id", 1, "ACTIVE");
        activeStatus.setHost("127.0.0.1:8080");
        NamespaceDO orderNamespace = buildNamespace("order_id", "订单发号", 1);
        NamespaceDO userNamespace = buildNamespace("user_id", "用户发号", 1);
        when(serviceInstanceConfigStatusService.listAll()).thenReturn(List.of(offlineStatus, activeStatus));
        when(namespaceService.listByCodes(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(orderNamespace, userNamespace));

        var response = serviceInstanceNodeManager.listNodes();

        assertEquals(2, response.getSummary().getTotalCount());
        assertEquals(1, response.getSummary().getOnlineCount());
        assertEquals(1, response.getSummary().getOfflineCount());
        assertTrue(response.getNodes().stream()
                .anyMatch(item -> "serialx-8080".equals(item.getInstanceId())
                        && "ACTIVE".equals(item.getRuntimeStatus())));
        assertTrue(response.getNodes().stream()
                .anyMatch(item -> "serialx-service-8081".equals(item.getInstanceId())
                        && "OFFLINE".equals(item.getRuntimeStatus())));
    }

    @Test
    void shouldIgnoreOfflineStatusWithoutMachineIdentity() {
        ServiceInstanceConfigStatusDO activeStatus = buildStatus("serialx-service-192.168.137.217", "order_id", 1, "ACTIVE");
        activeStatus.setHost("serialx-service/192.168.137.217:8080");
        ServiceInstanceConfigStatusDO staleStatus = buildStatus("serialx-service-8081", "user_id", 1, "OFFLINE");
        staleStatus.setHost(null);
        staleStatus.setLastOfflineTime(LocalDateTime.now());
        NamespaceDO orderNamespace = buildNamespace("order_id", "订单发号", 1);
        when(serviceInstanceConfigStatusService.listAll()).thenReturn(List.of(activeStatus, staleStatus));
        when(namespaceService.listByCodes(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(orderNamespace));

        var response = serviceInstanceNodeManager.listNodes();

        assertEquals(1, response.getSummary().getTotalCount());
        assertEquals(1, response.getSummary().getOnlineCount());
        assertEquals(0, response.getSummary().getOfflineCount());
        assertEquals("serialx-service-192.168.137.217", response.getNodes().getFirst().getInstanceId());
    }

    private ServiceInstanceConfigStatusDO buildStatus(String instanceId, String namespaceCode, int loadedVersion, String runtimeStatus) {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(instanceId);
        status.setHost("serialx-host/127.0.0.1");
        status.setNamespaceCode(namespaceCode);
        status.setLoadedVersion(loadedVersion);
        status.setRuntimeStatus(runtimeStatus);
        status.setLastReportTime(LocalDateTime.now());
        return status;
    }

    private NamespaceDO buildNamespace(String namespaceCode, String namespaceName, int currentVersion) {
        NamespaceDO namespace = new NamespaceDO();
        namespace.setNamespaceCode(namespaceCode);
        namespace.setNamespaceName(namespaceName);
        namespace.setCurrentVersion(currentVersion);
        return namespace;
    }
}
