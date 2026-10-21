package com.ronan.serialx.admin.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * Namespace 列表、详情及管理操作共用的控制面响应。
 *
 * <p>主记录信息、草稿和已发布配置分别表达管理侧状态，不能据此判断 service 实例已加载相应版本。
 */
@Getter
@Builder
public class NamespaceResponse {

    /**
     * 主键 ID。
     */
    private Long id;

    /**
     * Namespace 编码。
     */
    private String namespaceCode;

    /**
     * Namespace 名称。
     */
    private String namespaceName;

    /**
     * 主记录的发号模式：1 Segment、2 Snowflake；可能与尚未重新发布的历史配置不同。
     */
    private Integer idMode;

    /**
     * 主记录对应的模式名称，未知编码时为 null。
     */
    private String idModeName;

    /**
     * 管理侧状态：0 草稿、1 已启用、2 已停用，不表示 service 实例健康状态。
     */
    private Integer status;

    /**
     * 管理侧状态名称，未知编码时为 null。
     */
    private String statusName;

    /**
     * 负责人。
     */
    private String owner;

    /**
     * 备注说明。
     */
    private String remark;

    /**
     * 控制面当前指向的发布版本，未发布时为 0；回滚后可小于历史最大版本。
     */
    private Integer currentVersion;

    /**
     * 最新 EDITING 记录的配置内容，无草稿时为 null；保存草稿本身不会使其成为已发布配置。
     */
    private JsonNode draftConfig;

    /**
     * currentVersion 对应的已发布配置，未发布或对应记录缺失时为 null。
     * 不代表所有 service 实例已完成同步，停用时也可保留此配置供管理端查看。
     */
    private JsonNode publishedConfig;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
