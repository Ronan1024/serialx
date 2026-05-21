package com.ronan.serialx.application.auth;

/**
 * 应用凭证查询服务。
 */
public interface AppCredentialService {

    /**
     * 按 AppKey 查询应用凭证。
     */
    AppCredential getByAppKey(String appKey);
}
