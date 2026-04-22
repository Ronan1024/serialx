package com.ronan.serialx.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.infra.entity.AdminUserDO;

/**
 * 后台用户数据访问服务。
 */
public interface AdminUserService {

    /**
     * 根据主键查询后台用户。
     */
    AdminUserDO getById(Long id);

    /**
     * 根据用户名查询后台用户。
     */
    AdminUserDO getByUsername(String username);

    /**
     * 判断用户名是否已存在。
     */
    boolean existsByUsername(String username);

    /**
     * 分页查询后台用户。
     */
    Page<AdminUserDO> pageUsers(long pageNo, long pageSize, String keyword, Integer status);

    /**
     * 创建后台用户。
     */
    AdminUserDO create(AdminUserDO user);

    /**
     * 更新后台用户基础信息。
     */
    void update(AdminUserDO user);

    /**
     * 更新后台用户密码。
     */
    void updatePassword(Long id, String passwordHash);

    /**
     * 更新最后登录时间。
     */
    void updateLastLoginAt(Long id);

    /**
     * 删除后台用户。
     */
    void deleteById(Long id);
}
