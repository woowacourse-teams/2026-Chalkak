package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;

import com.chalkak.backend.config.SchedulingConfig;
import com.chalkak.backend.notification.service.NotificationRelayService;
import com.chalkak.backend.notification.service.TopicPushService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.ZonedDateTime;
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
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SqsPushConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SqsPushConfiguration.class,
                    NotificationRelayScheduler.class,
                    TopicPushScheduler.class
            )
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(NotificationRelayService.class, () -> mock(NotificationRelayService.class))
            .withBean(TopicPushService.class, () -> mock(TopicPushService.class))
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
                    assertThat(context).doesNotHaveBean(TopicPushScheduler.class);
                    assertThat(context).doesNotHaveBean("notificationTopicTaskScheduler");
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

    @Test
    @DisplayName("주제 발행은 Relay와 독립적으로 켜지고 전용 스케줄러를 사용한다")
    void configuration_topicEnabled_createsDedicatedSchedulerWithoutRelay() {
        // When & Then
        contextRunner.withPropertyValues(
                "chalkak.notification.sqs.topic-push-enabled=true",
                "chalkak.notification.sqs.relay-enabled=false",
                "chalkak.notification.sqs.queue-url=https://sqs.ap-northeast-2.amazonaws.com/000000000000/chalkak-dev-push"
        )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TopicPushScheduler.class);
                    assertThat(context).hasBean("notificationTopicTaskScheduler");
                    assertThat(context).doesNotHaveBean(NotificationRelayScheduler.class);
                });
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-12T08:59:59+09:00, 2026-10-12T09:00:00+09:00",
            "2026-10-12T09:00:00+09:00, 2026-10-12T09:01:00+09:00",
            "2026-10-12T09:00:30+09:00, 2026-10-12T09:01:00+09:00"
    })
    @DisplayName("주제 설정은 KST 매분 정각에 전용 스케줄러로 확인한다")
    void configuration_topicSchedule_pollsAtEveryMinuteBoundary(String now, String next)
            throws NoSuchMethodException {
        // Given
        Scheduled scheduled = TopicPushScheduler.class.getMethod("publishDueTopics")
                .getAnnotation(Scheduled.class);

        // When
        ZonedDateTime nextExecution = CronExpression.parse(scheduled.cron())
                .next(ZonedDateTime.parse(now));

        // Then
        assertThat(nextExecution).isEqualTo(ZonedDateTime.parse(next));
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        assertThat(scheduled.scheduler()).isEqualTo("notificationTopicTaskScheduler");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "REPLACE_WITH_QUEUE",
            "http://sqs.ap-northeast-2.amazonaws.com/000000000000/push",
            "https://sqs.ap-northeast-2.amazonaws.com/000000000000/push.fifo"})
    @DisplayName("주제 발행을 켜면 잘못된 큐 URL을 기동 시 거부한다")
    void configuration_topicEnabledWithInvalidQueue_rejectsConfiguration(String url) {
        // When & Then
        contextRunner.withPropertyValues("chalkak.notification.sqs.topic-push-enabled=true",
                "chalkak.notification.sqs.queue-url=" + url)
                .run(context -> assertThat(context).hasFailed());
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

    @ParameterizedTest
    @ValueSource(strings = {"relay", "topic"})
    @DisplayName("Relay·주제 발행 대기 중에도 공용 작업이 실행되고 전용 스케줄러는 앱 종료 시 종료된다")
    void configuration_publicationBlocked_keepsCommonSchedulerFreeAndClosesExecutor(String kind) {
        // Given
        FakePublicationProbe probe = new FakePublicationProbe();
        NotificationRelayService relay = mock(NotificationRelayService.class);
        TopicPushService topic = mock(TopicPushService.class);
        String enabledKey = "chalkak.notification.sqs.relay-enabled=true";
        String schedulerName = "notificationRelayTaskScheduler";
        String threadPrefix = "notification-relay-";
        if (kind.equals("topic")) {
            enabledKey = "chalkak.notification.sqs.topic-push-enabled=true";
            schedulerName = "notificationTopicTaskScheduler";
            threadPrefix = "notification-topic-";
        }
        String selectedScheduler = schedulerName;
        String selectedPrefix = threadPrefix;
        doAnswer(call -> {
            probe.publicationThread.set(Thread.currentThread().getName());
            probe.publicationStarted.countDown();
            probe.releasePublication.await(10, TimeUnit.SECONDS);
            return null;
        }).when(relay).publishPendingNotifications();
        doAnswer(call -> {
            probe.publicationThread.set(Thread.currentThread().getName());
            probe.publicationStarted.countDown();
            probe.releasePublication.await(10, TimeUnit.SECONDS);
            return null;
        }).when(topic).publishDueTopics();
        AtomicReference<ScheduledExecutorService> publicationExecutor = new AtomicReference<>();
        AtomicReference<ScheduledExecutorService> commonExecutor = new AtomicReference<>();
        // When
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(
                        SqsPushConfiguration.class,
                        NotificationRelayScheduler.class,
                        TopicPushScheduler.class,
                        SchedulingConfig.class
                )
                .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
                .withBean(NotificationRelayService.class, () -> relay)
                .withBean(TopicPushService.class, () -> topic)
                .withBean(FakePublicationProbe.class, () -> probe)
                .withPropertyValues(
                        "chalkak.notification.sqs.region=ap-northeast-2",
                        enabledKey,
                        "chalkak.notification.sqs.queue-url=https://sqs.ap-northeast-2.amazonaws.com/000000000000/chalkak-dev-push",
                        "spring.task.scheduling.pool.size=1"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // Then: 공용 스레드 하나만 있어도 발행 작업이 점유하지 않아야 한다.
                    if (kind.equals("topic")) {
                        // 분 경계를 기다리지 않고 Spring에 등록된 실제 주제 작업을 실행한다.
                        ScheduledAnnotationBeanPostProcessor processor = context.getBean(
                                ScheduledAnnotationBeanPostProcessor.class);
                        context.getBean(selectedScheduler, ThreadPoolTaskScheduler.class)
                                .execute(findTopicTask(processor));
                    }
                    try {
                        assertThat(probe.publicationStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        assertThat(probe.commonTaskRan.await(5, TimeUnit.SECONDS)).isTrue();
                        assertThat(probe.publicationThread.get()).startsWith(selectedPrefix);
                        assertThat(probe.commonThread.get())
                                .isNotEqualTo(probe.publicationThread.get());
                        ThreadPoolTaskScheduler dedicated = context.getBean(
                                selectedScheduler, ThreadPoolTaskScheduler.class);
                        ThreadPoolTaskScheduler common = context.getBean(
                                "taskScheduler", ThreadPoolTaskScheduler.class);
                        publicationExecutor.set(dedicated.getScheduledExecutor());
                        commonExecutor.set(common.getScheduledExecutor());
                        assertThat(publicationExecutor.get()).isNotSameAs(commonExecutor.get());
                        assertThat(dedicated.getScheduledThreadPoolExecutor().getCorePoolSize())
                                .isEqualTo(1);
                    } finally {
                        probe.releasePublication.countDown();
                    }
                });
        assertThat(publicationExecutor.get().isShutdown()).isTrue();
        assertThat(commonExecutor.get().isShutdown()).isTrue();
    }

    private Runnable findTopicTask(ScheduledAnnotationBeanPostProcessor processor) {
        for (ScheduledTask task : processor.getScheduledTasks()) {
            if (task.getTask() instanceof CronTask cronTask) {
                assertThat(cronTask.getExpression()).isEqualTo("0 * * * * *");
                return cronTask.getRunnable();
            }
        }
        throw new AssertionError("주제 푸시의 분 단위 예약 작업이 없습니다.");
    }

    static class FakePublicationProbe {
        private final CountDownLatch publicationStarted = new CountDownLatch(1);
        private final CountDownLatch releasePublication = new CountDownLatch(1);
        private final CountDownLatch commonTaskRan = new CountDownLatch(1);
        private final AtomicReference<String> publicationThread = new AtomicReference<>();
        private final AtomicReference<String> commonThread = new AtomicReference<>();

        @Scheduled(fixedDelay = 10)
        public void runCommonTask() {
            if (publicationStarted.getCount() == 0 && releasePublication.getCount() > 0) {
                commonThread.set(Thread.currentThread().getName());
                commonTaskRan.countDown();
            }
        }
    }
}
