package com.ronan.serialx.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NodeExecutorConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NodeExecutorConfig.class);

    @Test
    void springOwnsSeparateNamedExecutorsAndClosesThem() {
        var requestRef = new AtomicReference<ThreadPoolExecutor>();
        var schedulerRef = new AtomicReference<ScheduledThreadPoolExecutor>();
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            var requests = context.getBean("nodeRequestExecutor", ThreadPoolExecutor.class);
            var scheduler = context.getBean("nodeRenewScheduler", ScheduledThreadPoolExecutor.class);
            requestRef.set(requests);
            schedulerRef.set(scheduler);

            assertTrue(requests.submit(() -> Thread.currentThread().getName())
                    .get(2, TimeUnit.SECONDS).startsWith("serialx-node-request-"));
            assertTrue(scheduler.submit(() -> Thread.currentThread().getName())
                    .get(2, TimeUnit.SECONDS).startsWith("serialx-node-renew-"));
            assertTrue(requests.submit(() -> Thread.currentThread().isDaemon()).get(2, TimeUnit.SECONDS));
            assertTrue(scheduler.getRemoveOnCancelPolicy());
        });
        assertTrue(requestRef.get().isShutdown());
        assertTrue(schedulerRef.get().isShutdown());
    }

    @Test
    void saturatedRequestExecutorRejectsWithoutRunningOnCaller() {
        var entered = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        runner.run(context -> {
            var requests = context.getBean("nodeRequestExecutor", ThreadPoolExecutor.class);
            requests.execute(() -> {
                entered.countDown();
                try {
                    unblock.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                requests.execute(() -> {});
                assertEquals(1, requests.getQueue().size());
                assertThrows(RejectedExecutionException.class, () ->
                        requests.execute(() -> fail("饱和任务不能在调用线程执行")));
            } finally {
                unblock.countDown();
            }
        });
    }

    @Test
    void scheduledTasksDoNotUseNodeRenewScheduler() {
        runner.withUserConfiguration(ScheduledProbe.class).run(context -> {
            var probe = context.getBean(ScheduledProbe.class);
            assertTrue(probe.executed.await(2, TimeUnit.SECONDS));
            assertTrue(probe.threadName.get().startsWith("serialx-service-scheduled-"));
        });
    }

    @EnableScheduling
    static class ScheduledProbe {
        private final CountDownLatch executed = new CountDownLatch(1);
        private final AtomicReference<String> threadName = new AtomicReference<>();

        @Scheduled(fixedDelay = 60_000)
        public void run() {
            threadName.set(Thread.currentThread().getName());
            executed.countDown();
        }
    }
}
