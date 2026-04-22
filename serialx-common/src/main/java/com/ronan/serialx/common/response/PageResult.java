package com.ronan.serialx.common.response;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 分页查询结果。
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PageResult<T> {

    /**
     * 总记录数。
     */
    private long total;

    /**
     * 当前页码。
     */
    private long pageNo;

    /**
     * 每页记录数。
     */
    private long pageSize;

    /**
     * 当前页数据。
     */
    private List<T> records;

    /**
     * 构造分页查询结果。
     */
    public static <T> PageResult<T> of(long total, long pageNo, long pageSize, List<T> records) {
        return new PageResult<>(total, pageNo, pageSize, records);
    }
}
