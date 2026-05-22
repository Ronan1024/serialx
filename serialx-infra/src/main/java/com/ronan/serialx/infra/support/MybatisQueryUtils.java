package com.ronan.serialx.infra.support;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * MyBatis-Plus 查询辅助方法。
 */
public final class MybatisQueryUtils {

    private static final long FIRST_PAGE = 1L;

    private static final long ONE_ROW = 1L;

    private MybatisQueryUtils() {
    }

    /**
     * 只查询首条记录，不触发 count 查询，也不在业务代码中散落 SQL 片段。
     */
    public static <T> T selectFirst(BaseMapper<T> mapper, LambdaQueryWrapper<T> queryWrapper) {
        IPage<T> page = mapper.selectPage(Page.of(FIRST_PAGE, ONE_ROW, false), queryWrapper);
        if (page == null) {
            return null;
        }
        List<T> records = page.getRecords();
        if (records == null) {
            return null;
        }
        return records.isEmpty() ? null : records.getFirst();
    }
}
