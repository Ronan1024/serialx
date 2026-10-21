package com.ronan.serialx.common.request;

import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理接口共用的分页请求字段。
 *
 * <p>省略字段时使用初始化默认值；显式传入 null 不会自动恢复默认值。
 * 当前 Bean Validation 只约束 pageSize 的上限，不包含非空或正数校验。
 */
@Getter
@Setter
public class PageQueryRequest {

    /**
     * 当前页码，按从 1 开始的页码传入；省略时为第 1 页。
     */
    private Long pageNo = 1L;

    /**
     * 每页请求记录数，省略时为 20；启用 Bean Validation 时上限为 100。
     */
    @Max(value = 100, message = "查询记录数不能超过100条")
    private Long pageSize = 20L;
}
