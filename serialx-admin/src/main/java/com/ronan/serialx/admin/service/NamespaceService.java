package com.ronan.serialx.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.infra.entity.NamespaceDO;
import java.util.Collection;
import java.util.List;

/**
 * Namespace 数据访问服务。
 */
public interface NamespaceService {

    /**
     * 根据主键查询 Namespace。
     */
    NamespaceDO getById(Long id);

    /**
     * 根据编码查询 Namespace。
     */
    NamespaceDO getByCode(String namespaceCode);

    /**
     * 根据编码批量查询 Namespace。
     */
    List<NamespaceDO> listByCodes(Collection<String> namespaceCodes);

    /**
     * 判断编码是否存在。
     */
    boolean existsByCode(String namespaceCode);

    /**
     * 分页查询未逻辑删除的 Namespace 主记录
     *
     * <p>状态、模式与关键字筛选之间为 AND 关系；关键字内部对编码、名称、负责人做 OR 匹配。
     * 本查询不关联配置表，草稿与发布配置由业务编排层补齐。
     *
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页请求记录数
     * @param keyword SQL LIKE 关键字，无有效文本时忽略；非空白内容不做 trim 或通配符转义
     * @param status 状态编码，null 时不限；非 null 值直接按编码匹配
     * @param idMode 发号模式编码，null 时不限；非 null 值直接按编码匹配
     * @return 包含筛选后总记录数与当前页主记录的分页对象
     */
    Page<NamespaceDO> pageNamespaces(long pageNo, long pageSize, String keyword, Integer status, Integer idMode);

    /**
     * 创建 Namespace。
     */
    NamespaceDO create(NamespaceDO namespace);

    /**
     * 更新 Namespace。
     */
    void update(NamespaceDO namespace);

    /**
     * 按当前版本和状态推进发布版本。
     */
    boolean updateVersionAndStatus(Long id, Integer expectedVersion, Integer expectedStatus, Integer nextVersion, Integer nextStatus);

    /**
     * 按当前版本和状态更新状态。
     */
    boolean updateStatus(Long id, Integer expectedVersion, Integer expectedStatus, Integer nextStatus);

    /**
     * 按当前版本和状态回滚版本。
     */
    boolean updateCurrentVersion(Long id, Integer expectedVersion, Integer expectedStatus, Integer nextVersion);

    /**
     * 逻辑删除 Namespace。
     */
    boolean markDeleted(Long id, Integer expectedVersion, Integer expectedStatus);
}
