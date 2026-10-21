package com.ronan.serialx.application.node;

import org.springframework.stereotype.Component;

/**
 * 墙上时钟校验发号时间窗口，单调时钟计算租约剩余预算，两者不能混用。
 */
@Component
public class NodeClock {

    public long wallTimeMillis() {
        return System.currentTimeMillis();
    }

    public long nanoTime() {
        return System.nanoTime();
    }
}
