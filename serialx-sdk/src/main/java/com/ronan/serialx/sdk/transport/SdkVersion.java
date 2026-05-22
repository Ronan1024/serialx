package com.ronan.serialx.sdk.transport;

/**
 * SDK 版本解析。
 */
final class SdkVersion {

    private static final String FALLBACK_VERSION = "1.0-SNAPSHOT";

    private SdkVersion() {
    }

    static String current() {
        String implementationVersion = SdkVersion.class.getPackage().getImplementationVersion();
        return implementationVersion == null ? FALLBACK_VERSION : implementationVersion;
    }
}
