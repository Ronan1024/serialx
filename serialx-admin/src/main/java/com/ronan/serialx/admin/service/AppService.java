package com.ronan.serialx.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.infra.entity.AppDO;
import com.ronan.serialx.infra.entity.AppSecretDO;

/**
 * 业务调用方应用数据访问服务。
 */
public interface AppService {

    /**
     * 根据主键查询应用。
     */
    AppDO getById(Long id);

    /**
     * 根据 AppKey 查询应用。
     */
    AppDO getByAppKey(String appKey);

    /**
     * 判断 AppKey 是否已存在。
     */
    boolean existsByAppKey(String appKey);

    /**
     * 分页查询应用。
     */
    Page<AppDO> pageApps(long pageNo, long pageSize, String keyword, Integer status);

    /**
     * 创建应用与密钥。
     */
    AppDO create(AppDO app, AppSecretDO secret);

    /**
     * 更新应用基础信息。
     */
    void update(AppDO app);

    /**
     * 重置应用密钥。
     */
    void resetSecret(Long appId, String secretHash);
}
