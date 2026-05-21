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
     * 分页查询 Namespace。
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
