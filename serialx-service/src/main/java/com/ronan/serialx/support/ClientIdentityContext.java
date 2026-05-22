package com.ronan.serialx.support;

import java.util.Optional;

/**
 * 当前请求的客户端观测身份上下文。
 */
public final class ClientIdentityContext {

    private static final ThreadLocal<ClientIdentity> HOLDER = new ThreadLocal<>();

    private ClientIdentityContext() {
    }

    public static void set(ClientIdentity identity) {
        HOLDER.set(identity);
    }

    public static Optional<ClientIdentity> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
