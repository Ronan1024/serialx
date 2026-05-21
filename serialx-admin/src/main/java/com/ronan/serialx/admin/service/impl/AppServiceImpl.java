package com.ronan.serialx.admin.service.impl;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.admin.service.AppService;
import com.ronan.serialx.common.enums.AppStatusEnum;
import com.ronan.serialx.infra.entity.AppDO;
import com.ronan.serialx.infra.entity.AppSecretDO;
import com.ronan.serialx.infra.mapper.AppMapper;
import com.ronan.serialx.infra.mapper.AppSecretMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 业务调用方应用数据访问服务实现。
 */
@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {

    /**
     * 应用 Mapper。
     */
    private final AppMapper appMapper;

    /**
     * 应用密钥 Mapper。
     */
    private final AppSecretMapper appSecretMapper;

    @Override
    public AppDO getById(Long id) {
        return appMapper.selectById(id);
    }

    @Override
    public AppDO getByAppKey(String appKey) {
        return appMapper.selectOne(new LambdaQueryWrapper<AppDO>()
                .eq(AppDO::getAppKey, appKey));
    }

    @Override
    public boolean existsByAppKey(String appKey) {
        return appMapper.selectCount(new LambdaQueryWrapper<AppDO>()
                .eq(AppDO::getAppKey, appKey)) > 0;
    }

    @Override
    public Page<AppDO> pageApps(long pageNo, long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<AppDO> wrapper = new LambdaQueryWrapper<AppDO>()
                .eq(status != null, AppDO::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AppDO::getAppKey, keyword)
                        .or()
                        .like(AppDO::getAppName, keyword)
                        .or()
                        .like(AppDO::getOwner, keyword))
                .orderByDesc(AppDO::getId);
        return appMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppDO create(AppDO app, AppSecretDO secret) {
        app.setStatus(AppStatusEnum.ENABLED.getCode());
        appMapper.insert(app);

        secret.setAppId(app.getId());
        secret.setStatus(AppStatusEnum.ENABLED.getCode());
        appSecretMapper.insert(secret);
        return app;
    }

    @Override
    public void update(AppDO app) {
        appMapper.updateById(app);
    }

    @Override
    public void resetSecret(Long appId, String secretHash) {
        appSecretMapper.update(new LambdaUpdateWrapper<AppSecretDO>()
                .eq(AppSecretDO::getAppId, appId)
                .set(AppSecretDO::getSecretHash, secretHash)
                .set(AppSecretDO::getStatus, AppStatusEnum.ENABLED.getCode())
                .set(AppSecretDO::getUpdatedAt, LocalDateTime.now()));
    }
}
