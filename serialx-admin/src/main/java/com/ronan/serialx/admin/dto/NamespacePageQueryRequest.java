package com.ronan.serialx.admin.dto;

import com.ronan.serialx.common.request.PageQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Namespace 管理列表的分页筛选条件。
 *
 * <p>分页字段继承自 {@link PageQueryRequest}；关键字、状态与发号模式之间为 AND 关系。
 * 本请求只查询控制面数据，不查询 service 实例的配置同步状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NamespacePageQueryRequest extends PageQueryRequest {

    /**
     * 编码、名称或负责人的模糊匹配关键字，任一字段匹配即可。
     * null、空字符串及纯空白不参与筛选；其余内容原样用于 SQL LIKE，保留通配符语义。
     */
    private String keyword;

    /**
     * Namespace 状态：0 草稿、1 已启用、2 已停用；null 表示不限状态。
     * 对应 {@link com.ronan.serialx.common.enums.NamespaceStatusEnum}，按编码精确匹配。
     */
    private Integer status;

    /**
     * 发号模式：1 Segment、2 Snowflake；null 表示不限模式。
     * 对应 {@link com.ronan.serialx.common.enums.NamespaceIdModeEnum}，按 Namespace 主记录筛选。
     */
    private Integer idMode;
}
