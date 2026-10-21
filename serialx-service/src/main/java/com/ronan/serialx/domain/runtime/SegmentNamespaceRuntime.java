package com.ronan.serialx.domain.runtime;

import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;

/**
 * Segment 模式本地运行时。
 *
 * <p>Segment 模式必须依赖数据库号段租约，当前运行时只承载已加载状态，避免本地自增造成多实例重复发号。</p>
 */
public class SegmentNamespaceRuntime implements NamespaceRuntime {

    private final String namespaceCode;

    private final Integer version;

    private final Integer idMode;

    private final long initialValue;

    private final long maxValue;

    public SegmentNamespaceRuntime(String namespaceCode, Integer version, Integer idMode, long initialValue, long maxValue) {
        this.namespaceCode = namespaceCode;
        this.version = version;
        this.idMode = idMode;
        this.initialValue = initialValue;
        this.maxValue = maxValue;
    }

    @Override
    public String getNamespaceCode() {
        return namespaceCode;
    }

    @Override
    public Integer getVersion() {
        return version;
    }

    @Override
    public Integer getIdMode() {
        return idMode;
    }

    @Override
    public long nextId() {
        throw new BizException(
                BusinessErrorCode.ID_GENERATE_FAILED.getCode(),
                "Segment 发号需要数据库号段分配器，当前尚未启用，namespaceCode=" + namespaceCode);
    }

    /** 号段分配尚未实现，不能将已加载占位对象展示为 ACTIVE。 */
    @Override
    public String getRuntimeStatus() { return "UNSUPPORTED"; }
}
