package com.ronan.serialx.admin.manager.support;

import com.ronan.serialx.common.enums.NamespaceIdModeEnum;
import com.ronan.serialx.common.enums.NamespaceStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.common.util.Assert;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Namespace 顶层请求字段校验器。
 */
@Component
public class NamespaceRequestValidator {

    /**
     * 校验 Namespace 状态码。
     */
    public void validateStatus(Integer status) {
        Assert.isFalse(status != null && !NamespaceStatusEnum.contains(status), BusinessErrorCode.NAMESPACE_STATUS_INVALID);
    }

    /**
     * 校验 Namespace 发号模式。
     */
    public void validateIdMode(Integer idMode) {
        Assert.isFalse(
                idMode != null && !NamespaceIdModeEnum.contains(idMode),
                BizException.supplier(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "Namespace 发号模式非法"));
    }

    /**
     * 校验 Namespace 编码格式。
     */
    public void validateNamespaceCode(String namespaceCode) {
        Assert.isTrue(
                StringUtils.hasText(namespaceCode) && namespaceCode.trim().matches("^[a-zA-Z][a-zA-Z0-9_\\-]{2,63}$"),
                BizException.supplier(
                        BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(),
                        "namespace code must start with letter and contain 3-64 letters, numbers, '_' or '-'"));
    }

}
