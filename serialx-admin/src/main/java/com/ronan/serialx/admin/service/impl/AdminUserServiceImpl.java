package com.ronan.serialx.admin.service.impl;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.admin.service.AdminUserService;
import com.ronan.serialx.infra.entity.AdminUserDO;
import com.ronan.serialx.infra.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台用户数据访问服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /**
     * 后台用户 Mapper。
     */
    private final AdminUserMapper adminUserMapper;

    /**
     * 根据主键查询后台用户。
     */
    @Override
    public AdminUserDO getById(Long id) {
        return adminUserMapper.selectById(id);
    }

    /**
     * 根据用户名查询后台用户。
     */
    @Override
    public AdminUserDO getByUsername(String username) {
        return adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserDO>()
                .eq(AdminUserDO::getUsername, username));
    }

    /**
     * 判断用户名是否已存在。
     */
    @Override
    public boolean existsByUsername(String username) {
        return adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUserDO>()
                .eq(AdminUserDO::getUsername, username)) > 0;
    }

    /**
     * 分页查询后台用户。
     */
    @Override
    public Page<AdminUserDO> pageUsers(long pageNo, long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<AdminUserDO> wrapper = new LambdaQueryWrapper<AdminUserDO>()
                .eq(status != null, AdminUserDO::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AdminUserDO::getUsername, keyword)
                        .or()
                        .like(AdminUserDO::getDisplayName, keyword)
                        .or()
                        .like(AdminUserDO::getEmail, keyword))
                .orderByDesc(AdminUserDO::getId);
        return adminUserMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
    }

    /**
     * 创建后台用户。
     */
    @Override
    public AdminUserDO create(AdminUserDO user) {
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        adminUserMapper.insert(user);
        return user;
    }

    /**
     * 更新后台用户基础信息。
     */
    @Override
    public void update(AdminUserDO user) {
        user.setUpdatedAt(LocalDateTime.now());
        adminUserMapper.updateById(user);
    }

    /**
     * 更新后台用户密码哈希。
     */
    @Override
    public void updatePassword(Long id, String passwordHash) {
        adminUserMapper.update(new LambdaUpdateWrapper<AdminUserDO>()
                .eq(AdminUserDO::getId, id)
                .set(AdminUserDO::getPasswordHash, passwordHash)
                .set(AdminUserDO::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 更新最后登录时间。
     */
    @Override
    public void updateLastLoginAt(Long id) {
        adminUserMapper.update(new LambdaUpdateWrapper<AdminUserDO>()
                .eq(AdminUserDO::getId, id)
                .set(AdminUserDO::getLastLoginAt, LocalDateTime.now())
                .set(AdminUserDO::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 删除后台用户。
     */
    @Override
    public void deleteById(Long id) {
        adminUserMapper.deleteById(id);
    }
}
