package com.ronan.serialx.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.mapper.NamespaceConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NamespaceConfigServiceImplTest {
    private NamespaceConfigMapper mapper;
    private NamespaceConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), NamespaceConfigDO.class);
        mapper = mock(NamespaceConfigMapper.class);
        service = new NamespaceConfigServiceImpl(mapper);
    }

    @Test
    void shouldReturnZeroWithoutPublishedVersions() {
        when(mapper.selectPage(any(), any())).thenReturn(Page.of(1, 1, false));
        assertEquals(0, service.getMaxPublishedVersion(1L));
    }

    @Test
    void shouldReadOnlyHighestPublishedVersionForNamespace() {
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<NamespaceConfigDO> page = invocation.getArgument(0);
            LambdaQueryWrapper<NamespaceConfigDO> query = invocation.getArgument(1);
            assertEquals(1, page.getSize());
            assertFalse(page.searchCount());
            assertEquals("version", query.getSqlSelect());
            assertTrue(query.getSqlSegment().contains("ORDER BY version DESC"));
            assertTrue(query.getSqlSegment().contains("namespace_id ="));
            assertTrue(query.getSqlSegment().contains("publish_status ="));
            assertTrue(query.getParamNameValuePairs().containsValue(42L));
            assertTrue(query.getParamNameValuePairs().containsValue(1));
            page.setRecords(List.of(NamespaceConfigDO.builder().version(3).build()));
            return page;
        });
        assertEquals(3, service.getMaxPublishedVersion(42L));
    }

    @Test
    void shouldSkipEmptyBatchQueries() {
        assertEquals(List.of(), service.listEditingConfigs(List.of()));
        assertEquals(List.of(), service.listEditingConfigs(null));
        assertEquals(List.of(), service.listPublishedConfigsByVersions(Map.of()));
        assertEquals(List.of(), service.listPublishedConfigsByVersions(null));
        assertEquals(List.of(), service.listPublishedConfigsByVersions(Map.of(1L, 0, 2L, -1)));
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldReadBatchDraftsInDescendingIdOrder() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<NamespaceConfigDO> query = invocation.getArgument(0);
            String sql = renderedSql(query);
            assertTrue(sql.contains("namespace_id IN (11,22)"), sql);
            assertTrue(sql.contains("publish_status = 0"), sql);
            assertTrue(sql.endsWith("ORDER BY id DESC"), sql);
            return List.of(NamespaceConfigDO.builder().id(9L).namespaceId(11L).build());
        });

        assertEquals(9L, service.listEditingConfigs(List.of(11L, 22L)).getFirst().getId());
        verify(mapper).selectList(any());
    }

    @Test
    void shouldMatchEachNamespaceWithItsExactPublishedVersion() {
        Map<Long, Integer> versions = new java.util.LinkedHashMap<>();
        versions.put(11L, 2);
        versions.put(22L, 3);
        versions.put(33L, 0);
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<NamespaceConfigDO> query = invocation.getArgument(0);
            String sql = renderedSql(query);
            assertTrue(sql.contains("publish_status = 1 AND"), sql);
            assertTrue(sql.contains("(namespace_id = 11 AND version = 2) OR (namespace_id = 22 AND version = 3)"), sql);
            assertFalse(sql.contains("33"), sql);
            assertTrue(sql.endsWith("ORDER BY id DESC"), sql);
            return List.of(NamespaceConfigDO.builder().namespaceId(11L).version(2).build());
        });

        assertEquals(2, service.listPublishedConfigsByVersions(versions).getFirst().getVersion());
        verify(mapper).selectList(any());
    }

    private String renderedSql(LambdaQueryWrapper<NamespaceConfigDO> query) {
        String sql = query.getSqlSegment();
        for (var entry : query.getParamNameValuePairs().entrySet()) {
            sql = sql.replace("#{ew.paramNameValuePairs." + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return sql;
    }
}
