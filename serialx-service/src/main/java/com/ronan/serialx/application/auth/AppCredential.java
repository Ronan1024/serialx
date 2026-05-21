package com.ronan.serialx.application.auth;

import lombok.Builder;
import lombok.Getter;

/**
 * 应用调用凭证。
 */
@Getter
@Builder
public class AppCredential {

    /**
     * AppKey。
     */
    private String appKey;

    /**
     * 应用状态。
     */
    private Integer appStatus;

    /**
     * 密钥状态。
     */
    private Integer secretStatus;

    /**
     * AppSecret 摘要。
     */
    private String secretHash;
}
