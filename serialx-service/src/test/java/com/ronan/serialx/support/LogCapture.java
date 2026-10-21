package com.ronan.serialx.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/** 测试期间捕获指定类的真实日志，结束后恢复原有级别，避免污染其他用例。 */
public final class LogCapture implements AutoCloseable {
    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public LogCapture(Class<?> source, Level level) {
        logger = (Logger) LoggerFactory.getLogger(source);
        previousLevel = logger.getLevel();
        logger.setLevel(level);
        appender.start();
        logger.addAppender(appender);
    }

    public boolean contains(Level level, String message) {
        return appender.list.stream().anyMatch(event -> event.getLevel().equals(level)
                && event.getFormattedMessage().contains(message));
    }

    public String text() {
        return appender.list.stream().map(event -> event.getFormattedMessage()
                + (event.getThrowableProxy() == null ? "" : "\n" + ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }
}
