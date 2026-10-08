package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.auth.service.LoginSessionService;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.notification.service.DevicePushSender;
import com.chalkak.backend.notification.service.PushWorkerService;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "chalkak.notification.fcm", name = "worker-enabled", havingValue = "true")
public class PushWorkerConfiguration {
    private final String projectId;
    private final String credentialsPath;

    public PushWorkerConfiguration(
            @Value("${chalkak.notification.fcm.project-id:}") String projectId,
            @Value("${chalkak.notification.fcm.credentials-path:}") String credentialsPath) {
        if (projectId == null || projectId.isBlank()
                || credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalArgumentException(
                    "PushWorker를 켜려면 Firebase 프로젝트와 서버 인증 파일 경로가 필요합니다.");
        }
        this.projectId = projectId;
        this.credentialsPath = credentialsPath;
    }

    @Bean(destroyMethod = "shutdown")
    public FcmHttpTransport fcmHttpTransport(Clock clock, ObjectMapper mapper) {
        return new FcmHttpTransport(clock, mapper);
    }

    @Bean(destroyMethod = "delete")
    public FirebaseApp notificationFirebaseApp(FcmHttpTransport transport)
            throws IOException {
        try (var input = Files.newInputStream(Path.of(credentialsPath))) {
            return FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(ServiceAccountCredentials.fromStream(input))
                    .setProjectId(projectId)
                    .setHttpTransport(transport).setConnectTimeout(10000).setReadTimeout(10000)
                    .setWriteTimeout(10000).build(), "chalkak-notification");
        }
    }

    @Bean
    public DevicePushSender devicePushSender(
            FirebaseApp notificationFirebaseApp,
            FcmHttpTransport transport,
            Clock clock
    ) {
        return new FcmDevicePushSender(FirebaseMessaging.getInstance(notificationFirebaseApp),
                transport, clock);
    }

    @Bean
    public SqsClient notificationWorkerSqsClient(SqsPushProperties properties) {
        properties.validateQueueUrl();
        return SqsClient.builder().region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(25))
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()))
                .build();
    }

    @Bean
    public PushWorkerService pushWorkerService(
            NotificationRepository notificationRepository,
            PushDeviceRepository deviceRepository,
            LoginSessionService loginSessionService,
            DevicePushSender sender,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        return new PushWorkerService(notificationRepository, deviceRepository, loginSessionService,
                sender, clock, transactionManager);
    }

    // SQS 대기·발송은 전용 스레드에서 실행하고 애플리케이션 공용 스케줄러는 유지한다.
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler notificationPollingScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-poll-");
        return scheduler;
    }

    // 자동 연장 전용 실행기가 애플리케이션 공용 스케줄러를 대체하지 않게 한다.
    @Bean(defaultCandidate = false, destroyMethod = "shutdownNow")
    public ScheduledExecutorService notificationVisibilityExtensionExecutor() {
        return Executors.newSingleThreadScheduledExecutor();
    }

    @Bean
    public SqsPushService sqsPushService(
            @Qualifier("notificationWorkerSqsClient") SqsClient sqsClient,
            SqsPushProperties properties,
            ObjectMapper mapper,
            PushWorkerService service,
            Clock clock,
            @Qualifier("notificationVisibilityExtensionExecutor") ScheduledExecutorService visibilityExtensionExecutor
    ) {
        return new SqsPushService(sqsClient, properties.queueUrl(), mapper, service, clock,
                visibilityExtensionExecutor);
    }
}
