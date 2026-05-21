package com.ronan.serialx.admin.manager;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ronan.serialx.admin.dto.AppCreateRequest;
import com.ronan.serialx.admin.dto.AppResponse;
import com.ronan.serialx.admin.dto.AppSecretResetRequest;
import com.ronan.serialx.admin.dto.AppUpdateRequest;
import com.ronan.serialx.admin.service.AppService;
import com.ronan.serialx.common.enums.AppStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.response.PageResult;
import com.ronan.serialx.common.util.AppSignatureUtils;
import com.ronan.serialx.common.util.Assert;
import com.ronan.serialx.infra.entity.AppDO;
import com.ronan.serialx.infra.entity.AppSecretDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 业务调用方应用业务编排。
 */
@Component
@RequiredArgsConstructor
public class AppManager {

    /**
     * 应用数据访问服务。
     */
    private final AppService appService;

    /**
     * 分页查询应用。
     */
    public PageResult<AppResponse> pageApps(long pageNo, long pageSize, String keyword, Integer status) {
        validateStatus(status);
        long normalizedPageNo = Math.max(pageNo, 1);
        long normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<AppDO> page = appService.pageApps(normalizedPageNo, normalizedPageSize, keyword, status);
        List<AppResponse> records = page.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 查询应用详情。
     */
    public AppResponse getApp(Long id) {
        return toResponse(requireApp(id));
    }

    /**
     * 创建应用。
     */
    public AppResponse createApp(AppCreateRequest request) {
        String appKey = request.getAppKey().trim();
        Assert.isFalse(appService.existsByAppKey(appKey), BusinessErrorCode.APP_KEY_EXISTS);

        AppDO app = new AppDO();
        app.setAppKey(appKey);
        app.setAppName(request.getAppName().trim());
        app.setOwner(request.getOwner().trim());
        app.setRemark(normalizeNullable(request.getRemark()));

        AppSecretDO secret = new AppSecretDO();
        secret.setSecretHash(AppSignatureUtils.hashSecret(request.getAppSecret()));
        return toResponse(appService.create(app, secret));
    }

    /**
     * 更新应用基础信息。
     */
    public AppResponse updateApp(Long id, AppUpdateRequest request) {
        AppDO app = requireApp(id);
        validateStatus(request.getStatus());
        if (StringUtils.hasText(request.getAppName())) {
            app.setAppName(request.getAppName().trim());
        }
        if (StringUtils.hasText(request.getOwner())) {
            app.setOwner(request.getOwner().trim());
        }
        app.setStatus(request.getStatus() == null ? app.getStatus() : request.getStatus());
        app.setRemark(normalizeNullable(request.getRemark()));
        appService.update(app);
        return toResponse(requireApp(id));
    }

    /**
     * 重置应用密钥。
     */
    public void resetSecret(Long id, AppSecretResetRequest request) {
        requireApp(id);
        appService.resetSecret(id, AppSignatureUtils.hashSecret(request.getAppSecret()));
    }

    /**
     * 查询应用，不存在时抛出业务异常。
     */
    private AppDO requireApp(Long id) {
        AppDO app = appService.getById(id);
        Assert.notNull(app, BizException.supplier(BusinessErrorCode.APP_NOT_FOUND));
        return app;
    }

    /**
     * 校验应用状态码。
     */
    private void validateStatus(Integer status) {
        Assert.isFalse(status != null && !AppStatusEnum.contains(status), BusinessErrorCode.APP_PARAM_INVALID);
    }

    /**
     * 规范化可空字符串。
     */
    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 转换为应用响应对象。
     */
    private AppResponse toResponse(AppDO app) {
        return AppResponse.builder()
                .id(app.getId())
                .appKey(app.getAppKey())
                .appName(app.getAppName())
                .status(app.getStatus())
                .owner(app.getOwner())
                .remark(app.getRemark())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
