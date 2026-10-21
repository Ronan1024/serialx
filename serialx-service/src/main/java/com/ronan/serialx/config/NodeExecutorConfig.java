package com.ronan.serialx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 节点调用和续租调度的执行资源，统一创建并由 Spring 负责关闭。
 */
@Configuration(proxyBeanMethods = false)
public class NodeExecutorConfig {

    /**
     * 为普通 @Scheduled 任务提供默认调度器，避免框架自动复用节点续租调度器。
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("serialx-service-scheduled-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /**
     * 单线程执行节点调用，最多等待一个任务；饱和时拒绝，不能借用调用线程执行阻塞 IO。
     */
    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor nodeRequestExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), threads("serialx-node-request-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 调度线程会等待节点请求的结果，因此与请求执行器隔离。
     */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledThreadPoolExecutor nodeRenewScheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(1, threads("serialx-node-renew-"));
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    private ThreadFactory threads(String prefix) {
        var factory = new CustomizableThreadFactory(prefix);
        factory.setDaemon(true);
        return factory;
    }
}
