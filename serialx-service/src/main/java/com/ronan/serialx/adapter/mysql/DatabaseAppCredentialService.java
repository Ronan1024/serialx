package com.ronan.serialx.adapter.mysql;

import static com.ronan.serialx.infra.support.MybatisQueryUtils.selectFirst;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ronan.serialx.application.auth.AppCredential;
import com.ronan.serialx.application.auth.AppCredentialService;
import com.ronan.serialx.common.enums.AppStatusEnum;
import com.ronan.serialx.infra.entity.AppDO;
import com.ronan.serialx.infra.entity.AppSecretDO;
import com.ronan.serialx.infra.mapper.AppMapper;
import com.ronan.serialx.infra.mapper.AppSecretMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于数据库的应用凭证查询服务。
 */
@Service
@RequiredArgsConstructor
public class DatabaseAppCredentialService implements AppCredentialService {

    private final AppMapper appMapper;

    private final AppSecretMapper appSecretMapper;

    @Override
    public AppCredential getByAppKey(String appKey) {
        if (!StringUtils.hasText(appKey)) {
            return null;
        }
        AppDO app = selectFirst(appMapper, new LambdaQueryWrapper<AppDO>()
                .eq(AppDO::getAppKey, appKey)
                .orderByDesc(AppDO::getId));
        if (app == null) {
            return null;
        }
        AppSecretDO secret = selectFirst(appSecretMapper, new LambdaQueryWrapper<AppSecretDO>()
                .eq(AppSecretDO::getAppId, app.getId())
                .eq(AppSecretDO::getStatus, AppStatusEnum.ENABLED.getCode())
                .orderByDesc(AppSecretDO::getId));
        return AppCredential.builder()
                .appKey(app.getAppKey())
                .appStatus(app.getStatus())
                .secretStatus(secret == null ? null : secret.getStatus())
                .secretHash(secret == null ? null : secret.getSecretHash())
                .build();
    }
}
