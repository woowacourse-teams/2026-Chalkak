package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.auth.service.token.LoginSessionService;
import com.chalkak.backend.config.SchedulingConfig;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.notification.service.DevicePushSender;
import com.chalkak.backend.notification.service.PushWorkerService;
import com.chalkak.backend.post.repository.PostRepository;
import com.google.firebase.FirebaseApp;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.FixedDelayTask;
import java.time.Duration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PushWorkerConfigurationTest {
    @Test
    @DisplayName("Worker가 꺼져 있으면 Firebase 키 없이도 앱 컨텍스트를 만들 수 있다")
    void configuration_workerDisabled_doesNotInitializeFirebase() {
        // When & Then
        new ApplicationContextRunner().withUserConfiguration(PushWorkerConfiguration.class)
                .withPropertyValues("chalkak.notification.fcm.worker-enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(SqsPushService.class)
                        .doesNotHaveBean(PushWorkerService.class)
                        .doesNotHaveBean(DevicePushSender.class)
                        .doesNotHaveBean("notificationPollingScheduler")
                        .doesNotHaveBean("notificationPollingTasks"));
    }

    @ParameterizedTest
    @CsvSource(value = {"'',test.json", "test,''", "'',''"})
    @DisplayName("Worker를 켤 때 프로젝트나 서버 키 경로가 없으면 설정을 거절한다")
    void configuration_workerEnabledWithoutCredentials_rejectsConfiguration(
            String projectId, String credentialsPath) {
        // When & Then
        new ApplicationContextRunner().withUserConfiguration(PushWorkerConfiguration.class)
                .withPropertyValues("chalkak.notification.fcm.worker-enabled=true",
                        "chalkak.notification.fcm.project-id=" + projectId,
                        "chalkak.notification.fcm.credentials-path=" + credentialsPath)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasRootCauseMessage(
                                    "PushWorker를 켜려면 Firebase 프로젝트와 서버 인증 파일 경로가 필요합니다.");
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "http://sqs.ap-northeast-2.amazonaws.com/000000000000/test",
            "https://example.com/000000000000/test",
            "https://sqs.ap-northeast-2.amazonaws.com/123/test",
            "https://sqs.ap-northeast-2.amazonaws.com/000000000000/test.fifo"})
    @DisplayName("Relay와 Worker는 같은 조건으로 잘못된 Standard 큐 URL을 거절한다")
    void configuration_invalidQueueUrl_rejectsRelayAndWorker(String queueUrl) {
        // Given
        PushWorkerConfiguration configuration = new PushWorkerConfiguration("test", "test.json");
        SqsPushProperties properties = new SqsPushProperties(false, false, queueUrl,
                "ap-northeast-2");
        // When & Then
        assertThatThrownBy(() -> new SqsPushProperties(true, false, queueUrl, "ap-northeast-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 푸시를 사용하려면 Standard SQS 큐 URL이 필요합니다.");
        assertThatThrownBy(() -> new SqsPushProperties(false, true, queueUrl, "ap-northeast-2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.notificationWorkerSqsClient(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 푸시를 사용하려면 Standard SQS 큐 URL이 필요합니다.");
    }

    @Test
    @DisplayName("두 폴링이 동시에 대기해도 공용 작업이 실행되고 컨텍스트 종료 시 실행기가 종료된다")
    void configuration_twoPollsBlocked_keepsCommonSchedulerFreeAndClosesExecutors() {
        // Given
        FakePollingProbe probe = new FakePollingProbe();
        AtomicReference<ScheduledExecutorService> visibilityExtensionExecutor = new AtomicReference<>();
        AtomicReference<ScheduledExecutorService> pollingExecutor = new AtomicReference<>();
        AtomicReference<ScheduledExecutorService> commonExecutor = new AtomicReference<>();
        // When
        createPollingContext(probe)
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasBean("taskScheduler").hasBean("notificationPollingScheduler")
                            .hasSingleBean(PushWorkerService.class)
                            .hasSingleBean(SqsPushService.class)
                            .hasSingleBean(DevicePushSender.class);
                    ScheduledExecutorService executor = context.getBean(
                            "notificationVisibilityExtensionExecutor",
                            ScheduledExecutorService.class);
                    ThreadPoolTaskScheduler scheduler = context.getBean("taskScheduler",
                            ThreadPoolTaskScheduler.class);
                    ThreadPoolTaskScheduler polling = context.getBean(
                            "notificationPollingScheduler",
                            ThreadPoolTaskScheduler.class);
                    visibilityExtensionExecutor.set(executor);
                    pollingExecutor.set(polling.getScheduledExecutor());
                    commonExecutor.set(scheduler.getScheduledExecutor());
                    // Then
                    assertThat(scheduler.getScheduledExecutor()).isNotSameAs(executor);
                    assertThat(polling.getScheduledExecutor()).isNotSameAs(executor)
                            .isNotSameAs(scheduler.getScheduledExecutor());
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                            .isEqualTo(4);
                    assertThat(polling.getScheduledThreadPoolExecutor().getCorePoolSize())
                            .isEqualTo(2);
                    assertThat(executor.isShutdown()).isFalse();
                    assertThat(context
                            .getBean("notificationPollingTasks", ScheduledTaskRegistrar.class)
                            .getScheduledTasks()).hasSize(2).extracting(task -> task.getTask())
                            .allSatisfy(task -> assertThat(task).isInstanceOf(FixedDelayTask.class))
                            .extracting(task -> ((FixedDelayTask) task).getIntervalDuration())
                            .containsExactly(Duration.ofMillis(1), Duration.ofMillis(1));
                    try {
                        assertThat(probe.pollStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        assertThat(probe.commonTaskRan.await(5, TimeUnit.SECONDS)).isTrue();
                        assertThat(probe.pollThreads).hasSize(2)
                                .allSatisfy(
                                        name -> assertThat(name).startsWith("notification-poll-"));
                        assertThat(probe.commonThread.get()).isNotIn(probe.pollThreads)
                                .doesNotStartWith("notification-poll-");
                    } finally {
                        probe.releasePoll.countDown();
                    }
                });
        assertThat(visibilityExtensionExecutor.get().isShutdown()).isTrue();
        assertThat(pollingExecutor.get().isShutdown()).isTrue();
        assertThat(commonExecutor.get().isShutdown()).isTrue();
        try {
            assertThat(pollingExecutor.get().awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(visibilityExtensionExecutor.get().awaitTermination(5, TimeUnit.SECONDS))
                    .isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("실행기 종료 확인 중 인터럽트", exception);
        }
    }

    @Test
    @DisplayName("공용 스케줄링을 끄면 Worker의 폴링 작업도 등록하지 않는다")
    void configuration_schedulingDisabled_doesNotRegisterPolling() {
        // Given
        FakePollingProbe probe = new FakePollingProbe();
        // When & Then
        createPollingContext(probe).withPropertyValues("chalkak.scheduling.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean("notificationPollingTasks");
                    assertThat(probe.pollThreads).isEmpty();
                    org.mockito.Mockito.verifyNoInteractions(context.getBean(SqsClient.class));
                });
    }

    private ApplicationContextRunner createPollingContext(FakePollingProbe probe) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SchedulingTestConfiguration.class, SchedulingConfig.class)
                .withPropertyValues("spring.task.scheduling.pool.size=4",
                        "chalkak.notification.fcm.worker-enabled=true",
                        "chalkak.notification.fcm.project-id=test",
                        "chalkak.notification.fcm.credentials-path=test.json")
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(FakePollingProbe.class, () -> probe)
                .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
                .withBean(SqsPushProperties.class, () -> new SqsPushProperties(false, false,
                        "https://sqs.ap-northeast-2.amazonaws.com/000000000000/test",
                        "ap-northeast-2"))
                .withBean(NotificationRepository.class, () -> mock(NotificationRepository.class))
                .withBean(TopicRepository.class, () -> mock(TopicRepository.class))
                .withBean(PostRepository.class, () -> mock(PostRepository.class))
                .withBean(PushDeviceRepository.class, () -> mock(PushDeviceRepository.class))
                .withBean(LoginSessionService.class, () -> mock(LoginSessionService.class))
                .withBean(PlatformTransactionManager.class,
                        () -> mock(PlatformTransactionManager.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class SchedulingTestConfiguration extends PushWorkerConfiguration {
        private final FakePollingProbe probe;

        SchedulingTestConfiguration(
                @Value("${chalkak.notification.fcm.project-id:}") String projectId,
                @Value("${chalkak.notification.fcm.credentials-path:}") String credentialsPath,
                FakePollingProbe probe) {
            super(projectId, credentialsPath);
            this.probe = probe;
        }

        @Bean
        @Override
        public FirebaseApp notificationFirebaseApp() {
            return mock(FirebaseApp.class);
        }

        @Bean
        @Override
        public DevicePushSender devicePushSender(FirebaseApp app, Clock clock) {
            return mock(DevicePushSender.class);
        }

        @Bean
        @Override
        public SqsClient notificationWorkerSqsClient(SqsPushProperties properties) {
            SqsClient client = mock(SqsClient.class);
            given(client.receiveMessage(
                    org.mockito.ArgumentMatchers.<Consumer<ReceiveMessageRequest.Builder>>any()))
                    .willAnswer(call -> {
                        probe.pollThreads.add(Thread.currentThread().getName());
                        probe.pollStarted.countDown();
                        probe.releasePoll.await(10, TimeUnit.SECONDS);
                        return ReceiveMessageResponse.builder().build();
                    });
            return client;
        }
    }

    static class FakePollingProbe {
        private final CountDownLatch pollStarted = new CountDownLatch(2);
        private final CountDownLatch releasePoll = new CountDownLatch(1);
        private final CountDownLatch commonTaskRan = new CountDownLatch(1);
        private final Set<String> pollThreads = ConcurrentHashMap.newKeySet();
        private final AtomicReference<String> commonThread = new AtomicReference<>();

        @Scheduled(fixedDelay = 10)
        public void runCommonTask() {
            if (pollStarted.getCount() == 0 && releasePoll.getCount() > 0) {
                commonThread.set(Thread.currentThread().getName());
                commonTaskRan.countDown();
            }
        }
    }
}
