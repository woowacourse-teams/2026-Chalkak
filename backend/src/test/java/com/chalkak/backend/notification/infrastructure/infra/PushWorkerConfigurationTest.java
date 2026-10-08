package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.service.LoginSessionService;
import com.chalkak.backend.config.SchedulingConfig;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.notification.service.DevicePushSender;
import com.chalkak.backend.notification.service.PushWorkerService;
import com.google.firebase.FirebaseApp;
import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
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
                        .doesNotHaveBean(DevicePushSender.class));
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
        SqsPushProperties properties = new SqsPushProperties(false, queueUrl, "ap-northeast-2");
        // When & Then
        assertThatThrownBy(() -> new SqsPushProperties(true, queueUrl, "ap-northeast-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 푸시를 사용하려면 Standard SQS 큐 URL이 필요합니다.");
        assertThatThrownBy(() -> configuration.notificationWorkerSqsClient(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 푸시를 사용하려면 Standard SQS 큐 URL이 필요합니다.");
    }

    @Test
    @DisplayName("자동 연장 실행기는 공용 4개 스레드 스케줄러와 분리되고 컨텍스트 종료 시 종료된다")
    void configuration_visibilityExtensionEnabled_keepsSchedulerSeparateAndClosesExecutor() {
        AtomicReference<ScheduledExecutorService> visibilityExtensionExecutor = new AtomicReference<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SchedulingTestConfiguration.class, SchedulingConfig.class)
                .withPropertyValues("spring.task.scheduling.pool.size=4",
                        "chalkak.notification.fcm.worker-enabled=true",
                        "chalkak.notification.fcm.project-id=test",
                        "chalkak.notification.fcm.credentials-path=test.json")
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
                .withBean(SqsPushProperties.class, () -> new SqsPushProperties(false,
                        "https://sqs.ap-northeast-2.amazonaws.com/000000000000/test",
                        "ap-northeast-2"))
                .withBean(NotificationRepository.class, () -> mock(NotificationRepository.class))
                .withBean(PushDeviceRepository.class, () -> mock(PushDeviceRepository.class))
                .withBean(LoginSessionService.class, () -> mock(LoginSessionService.class))
                .withBean(PlatformTransactionManager.class,
                        () -> mock(PlatformTransactionManager.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ThreadPoolTaskScheduler.class)
                            .hasSingleBean(PushWorkerService.class)
                            .hasSingleBean(SqsPushService.class)
                            .hasSingleBean(DevicePushSender.class);
                    ScheduledExecutorService executor = context.getBean(
                            "notificationVisibilityExtensionExecutor",
                            ScheduledExecutorService.class);
                    ThreadPoolTaskScheduler scheduler = context
                            .getBean(ThreadPoolTaskScheduler.class);
                    visibilityExtensionExecutor.set(executor);
                    assertThat(scheduler.getScheduledExecutor()).isNotSameAs(executor);
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                            .isEqualTo(4);
                    assertThat(executor.isShutdown()).isFalse();
                });
        assertThat(visibilityExtensionExecutor.get().isShutdown()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static class SchedulingTestConfiguration extends PushWorkerConfiguration {
        SchedulingTestConfiguration(
                @Value("${chalkak.notification.fcm.project-id:}") String projectId,
                @Value("${chalkak.notification.fcm.credentials-path:}") String credentialsPath) {
            super(projectId, credentialsPath);
        }

        @Bean
        @Override
        public FcmHttpTransport fcmHttpTransport(Clock clock, ObjectMapper mapper) {
            return mock(FcmHttpTransport.class);
        }

        @Bean
        @Override
        public FirebaseApp notificationFirebaseApp(FcmHttpTransport transport) {
            return mock(FirebaseApp.class);
        }

        @Bean
        @Override
        public DevicePushSender devicePushSender(FirebaseApp app, FcmHttpTransport transport,
                Clock clock) {
            return mock(DevicePushSender.class);
        }

        @Bean
        @Override
        public SqsClient notificationWorkerSqsClient(SqsPushProperties properties) {
            SqsClient client = mock(SqsClient.class);
            given(client.receiveMessage(
                    org.mockito.ArgumentMatchers.<Consumer<ReceiveMessageRequest.Builder>>any()))
                    .willReturn(ReceiveMessageResponse.builder().build());
            return client;
        }
    }
}
