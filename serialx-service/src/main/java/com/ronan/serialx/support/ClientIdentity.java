package com.ronan.serialx.support;

import lombok.Builder;
import lombok.Getter;

/**
 * SDK 客户端上报的观测身份。
 */
@Getter
@Builder
public class ClientIdentity {

    public static final String HEADER_CLIENT_NAME = "X-SerialX-Client-Name";

    public static final String HEADER_CLIENT_ID = "X-SerialX-Client-Id";

    public static final String HEADER_CLIENT_VERSION = "X-SerialX-Client-Version";

    public static final String HEADER_INSTANCE_ID = "X-SerialX-Instance-Id";

    public static final String HEADER_SDK_VERSION = "X-SerialX-Sdk-Version";

    private final String clientName;

    private final String clientId;

    private final String clientVersion;

    private final String instanceId;

    private final String sdkVersion;
}
