package com.ronan.serialx.admin.manager.support.config;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import lombok.Getter;
import lombok.Setter;

/**
 * Segment 模式配置。
 */
@Getter
@Setter
public class SegmentNamespaceConfig implements NamespaceModeConfig {

    /**
     * 号段步长。
     */
    private Integer stepSize;

    /**
     * 初始值。
     */
    private Long initialValue;

    /**
     * 缓存号段数量。
     */
    private Integer cacheSegments;

    /**
     * 最大值。
     */
    private Long maxValue;

    @Override
    public void validate() {
        requirePositive(stepSize, "stepSize");
        requireNonNegative(initialValue, "initialValue");
        requirePositive(cacheSegments, "cacheSegments");
        if (maxValue != null && maxValue <= 0) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "maxValue 必须大于 0");
        }
        if (maxValue != null && initialValue != null && maxValue < initialValue) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "maxValue 必须大于等于 initialValue");
        }
    }

    private void requirePositive(Number value, String fieldName) {
        if (value == null || value.longValue() <= 0) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), fieldName + " 必须大于 0");
        }
    }

    private void requireNonNegative(Number value, String fieldName) {
        if (value == null || value.longValue() < 0) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), fieldName + " 必须大于等于 0");
        }
    }
}
