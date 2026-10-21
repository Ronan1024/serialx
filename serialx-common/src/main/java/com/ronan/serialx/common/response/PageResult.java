package com.ronan.serialx.common.response;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 管理接口统一的分页查询结果。
 *
 * @param <T> 当前页记录的响应类型
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PageResult<T> {

    /**
     * 满足查询条件的总记录数，不是当前页条数，也不是未筛选的全库总量。
     */
    private long total;

    /**
     * 查询结果的当前页码，正常分页从 1 开始。
     */
    private long pageNo;

    /**
     * 分页容量；末页或空页的实际记录数可以小于该值。
     */
    private long pageSize;

    /**
     * 当前页记录，不包含其他页；通常在查询无结果或超出末页时为空列表。
     */
    private List<T> records;

    /**
     * 使用数据访问层的分页信息与转换后的当前页记录构造响应。
     *
     * <p>仅封装传入值，不重新统计、裁剪记录或纠正分页参数；records 沿用传入列表引用。
     *
     * @param total 满足筛选条件的总记录数
     * @param pageNo 查询结果页码
     * @param pageSize 分页容量
     * @param records 保持原查询顺序的当前页记录
     * @param <T> 当前页记录的响应类型
     * @return 不依赖具体分页框架的响应结构
     */
    public static <T> PageResult<T> of(long total, long pageNo, long pageSize, List<T> records) {
        return new PageResult<>(total, pageNo, pageSize, records);
    }
}
