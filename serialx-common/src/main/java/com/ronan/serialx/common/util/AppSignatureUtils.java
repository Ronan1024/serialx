package com.ronan.serialx.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.ronan.serialx.common.error.SystemErrorCode;
import com.ronan.serialx.common.exception.BizException;

/**
 * 应用签名工具。
 */
public final class AppSignatureUtils {

    /**
     * HMAC SHA-256 算法名称。
     */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * SHA-256 算法名称。
     */
    private static final String SHA_256 = "SHA-256";

    private AppSignatureUtils() {
    }

    /**
     * 计算 AppSecret 的不可逆摘要。
     */
    public static String hashSecret(String appSecret) {
        return sha256Hex(appSecret == null ? "" : appSecret);
    }

    /**
     * 构建签名原文。
     */
    public static String canonicalString(String method, String path, String timestamp, String nonce, byte[] body) {
        return normalize(method) + "\n"
                + normalize(path) + "\n"
                + normalize(timestamp) + "\n"
                + normalize(nonce) + "\n"
                + sha256Hex(body == null ? new byte[0] : body);
    }

    /**
     * 使用 AppSecret 摘要作为 HMAC 密钥生成签名。
     */
    public static String signWithSecretHash(String secretHash, String canonicalString) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(HexFormat.of().parseHex(secretHash), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BizException(SystemErrorCode.CONFIG_ERROR.getCode(), "应用签名配置异常");
        }
    }

    /**
     * 常量时间比较签名，降低时序侧信道风险。
     */
    public static boolean signatureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组 SHA-256 摘要。
     */
    public static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(SystemErrorCode.CONFIG_ERROR.getCode(), "SHA-256 算法不可用");
        }
    }

    /**
     * 计算字符串 SHA-256 摘要。
     */
    private static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 规范化签名字段。
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
