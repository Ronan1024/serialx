package com.ronan.serialx.admin.manager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.admin.dto.AppCreateRequest;
import com.ronan.serialx.admin.service.AppService;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppManagerTest {
    @Mock private AppService service;
    private AppManager manager;

    @BeforeEach
    void setUp() {
        manager = new AppManager(service);
    }

    @Test
    void shouldAllowValidPageStatus() {
        when(service.pageApps(1, 20, null, 1)).thenReturn(Page.of(1, 20));
        assertEquals(0, manager.pageApps(1, 20, null, 1).getTotal());
    }

    @Test
    void shouldRejectInvalidPageStatus() {
        assertEquals(BusinessErrorCode.APP_PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> manager.pageApps(1, 20, null, 99)).getCode());
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectMissingApp() {
        assertEquals(BusinessErrorCode.APP_NOT_FOUND.getCode(),
                assertThrows(BizException.class, () -> manager.getApp(99L)).getCode());
    }

    @Test
    void shouldRejectExistingAppKey() {
        AppCreateRequest request = new AppCreateRequest();
        request.setAppKey("existing");
        when(service.existsByAppKey("existing")).thenReturn(true);
        assertEquals(BusinessErrorCode.APP_KEY_EXISTS.getCode(),
                assertThrows(BizException.class, () -> manager.createApp(request)).getCode());
        verify(service, never()).create(any(), any());
    }
}
