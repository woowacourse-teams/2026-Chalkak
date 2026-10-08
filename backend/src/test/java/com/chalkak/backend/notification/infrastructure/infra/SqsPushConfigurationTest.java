package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;

import com.chalkak.backend.config.SchedulingConfig;
import com.chalkak.backend.notification.service.NotificationRelayService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SqsPushConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SqsPushConfiguration.class, NotificationRelayScheduler.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(NotificationRelayService.class, () -> mock(NotificationRelayService.class))
            .withPropertyValues("chalkak.notification.sqs.region=ap-northeast-2");

    @Test
    @DisplayName("Relay가 꺼져 있으면 AWS 자격증명이나 큐 없이 기동하고 스케줄러는 만들지 않는다")
    void configuration_disabled_startsWithoutRelayScheduler() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.notification.sqs.relay-enabled=false",
                        "chalkak.notification.sqs.queue-url=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationRelayScheduler.class);
                    assertThat(context).doesNotHaveBean("notificationRelayTaskScheduler");
                });
    }

    @Test
    @DisplayName("Relay를 켜고 Standard 큐 URL을 지정하면 스케줄러를 만든다")
    void configuration_enabledWithQueue_createsRelayScheduler() {
        // When & Then
        contextRunner.withPropertyValues("chalkak.notification.sqs.relay-enabled=true",
                "chalkak.notification.sqs.queue-url=https://sqs.ap-northeast-2.amazonaws.com/000000000000/chalkak-dev-push")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationRelayScheduler.class);
                    assertThat(context).hasBean("notificationRelayTaskScheduler");
                });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "REPLACE_WITH_QUEUE",
            "http://sqs.ap-northeast-2.amazonaws.com/000000000000/push",
            "https://sqs.ap-northeast-2.amazonaws.com/000000000000/push.fifo"})
    @DisplayName("Relay를 켜면 누락·부적절한 URL·FIFO 큐 설정을 기동 때 거부한다")
    void configuration_enabledWithInvalidQueue_rejectsConfiguration(String url) {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.notification.sqs.relay-enabled=true",
                        "chalkak.notification.sqs.queue-url=" + url)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Relay 발행 대기 중에도 공용 작업이 실행되고 전용 스케줄러는 앱 종료 시 종료된다")
    void configuration_relayBlocked_keepsCommonSchedulerFreeAndClosesExecutor() {
        // Given
        FakeRelayProbe probe = new FakeRelayProbe();
        NotificationRelayService relay = mock(NotificationRelayService.class);
        doAnswer(call -> {
            probe.relayThread.set(Thread.currentThread().getName());
            probe.relayStarted.countDown();
            probe.releaseRelay.await(10, TimeUnit.SECONDS);
            return null;
        }).when(relay).publishPendingNotifications();
        AtomicReference<ScheduledExecutorService> relayExecutor = new AtomicReference<>();
        AtomicReference<ScheduledExecutorService> commonExecutor = new AtomicReference<>();
        // When
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SqsPushConfiguration.class,
                        NotificationRelayScheduler.class, SchedulingConfig.class)
                .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
                .withBean(NotificationRelayService.class, () -> relay)
                .withBean(FakeRelayProbe.class, () -> probe)
                .withPropertyValues("chalkak.notification.sqs.region=ap-northeast-2",
                        "chalkak.notification.sqs.relay-enabled=true",
                        "chalkak.notification.sqs.queue-url=https://sqs.ap-northeast-2.amazonaws.com/000000000000/chalkak-dev-push",
                        "spring.task.scheduling.pool.size=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // Then: 공용 스레드 하나만 있어도 Relay가 점유하지 않아야 한다.
                    try {
                        assertThat(probe.relayStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        assertThat(probe.commonTaskRan.await(5, TimeUnit.SECONDS)).isTrue();
                        assertThat(probe.relayThread.get()).startsWith("notification-relay-");
                        assertThat(probe.commonThread.get()).isNotEqualTo(probe.relayThread.get());
                        ThreadPoolTaskScheduler dedicated = context.getBean(
                                "notificationRelayTaskScheduler", ThreadPoolTaskScheduler.class);
                        ThreadPoolTaskScheduler common = context.getBean(
                                "taskScheduler", ThreadPoolTaskScheduler.class);
                        relayExecutor.set(dedicated.getScheduledExecutor());
                        commonExecutor.set(common.getScheduledExecutor());
                        assertThat(relayExecutor.get()).isNotSameAs(commonExecutor.get());
                        assertThat(dedicated.getScheduledThreadPoolExecutor().getCorePoolSize())
                                .isEqualTo(1);
                    } finally {
                        probe.releaseRelay.countDown();
                    }
                });
        assertThat(relayExecutor.get().isShutdown()).isTrue();
        assertThat(commonExecutor.get().isShutdown()).isTrue();
    }

    static class FakeRelayProbe {
        private final CountDownLatch relayStarted = new CountDownLatch(1);
        private final CountDownLatch releaseRelay = new CountDownLatch(1);
        private final CountDownLatch commonTaskRan = new CountDownLatch(1);
        private final AtomicReference<String> relayThread = new AtomicReference<>();
        private final AtomicReference<String> commonThread = new AtomicReference<>();

        @Scheduled(fixedDelay = 10)
        public void runCommonTask() {
            if (relayStarted.getCount() == 0 && releaseRelay.getCount() > 0) {
                commonThread.set(Thread.currentThread().getName());
                commonTaskRan.countDown();
            }
        }
    }
}
