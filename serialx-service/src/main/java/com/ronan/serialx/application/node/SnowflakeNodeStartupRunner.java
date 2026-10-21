package com.ronan.serialx.application.node;

import com.ronan.serialx.application.refresh.NamespaceRuntimeLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 先加载 Namespace 再开放发号；任一步骤失败均关闭并释放节点。
 */
@Component
@Slf4j
@Order(0)
@RequiredArgsConstructor
public class SnowflakeNodeStartupRunner implements ApplicationRunner {

    private final SnowflakeNodeLifecycle nodeLifecycle;
    private final NamespaceRuntimeLoader namespaceRuntimeLoader;

    @Override
    public void run(ApplicationArguments args) {
        log.info("发号服务配置初始化开始");
        try {
            namespaceRuntimeLoader.loadPublishedNamespaces();
            nodeLifecycle.markReady();
            log.info("发号服务配置初始化完成，节点已就绪");
        } catch (RuntimeException ex) {
            log.error("发号服务配置初始化失败，关闭节点，exceptionType={}", ex.getClass().getSimpleName());
            nodeLifecycle.close();
            throw ex;
        }
    }
}
