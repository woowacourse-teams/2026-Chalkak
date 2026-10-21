package com.chalkak.backend.aireview.infrastructure.infra;

import com.chalkak.backend.aireview.infrastructure.infra.openai.OpenAiProperties;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.aireview.repository.AiReviewRepository;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequestFactory;
import com.chalkak.backend.aireview.service.AiReviewGenerator;
import com.chalkak.backend.aireview.service.AiReviewRetryBackoff;
import com.chalkak.backend.aireview.service.AiReviewWorkerService;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "chalkak.ai-review.worker", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiReviewWorkerProperties.class)
public class AiReviewWorkerConfiguration {

    // 종료에는 이 유예 외에도 Tomcat의 graceful shutdown과 관리자 미리보기의 동기 OpenAI 호출 대기가 더해질 수 있어
    // systemd의 TimeoutStopSec(30초)보다 넉넉히 짧게 둔다. 끝내지 못한 행은 임대 만료 뒤 회수되므로 데이터는 안전하다.
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(15);
    // 호출이 끝난 뒤 결과를 반영하고 로그를 남길 여유
    private static final Duration LEASE_MARGIN = Duration.ofSeconds(20);

    // 폴러는 짧은 가져가기만 하므로 한 스레드면 충분하고, 공용 스케줄러를 밀어내지 않게 전용으로 둔다.
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler aiReviewWorkerScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ai-review-poll-");
        return scheduler;
    }

    @Bean
    public AiReviewWorkerService aiReviewWorkerService(
            AiReviewRepository reviewRepository,
            AiPromptVersionRepository promptVersionRepository,
            PostRepository postRepository,
            AiReviewGenerator aiReviewGenerator,
            AiReviewGenerationRequestFactory requestFactory,
            PlatformTransactionManager transactionManager,
            Clock clock,
            OpenAiProperties openAiProperties,
            AiReviewWorkerProperties properties
    ) {
        validateLease(properties.leaseDuration(), openAiProperties);
        return new AiReviewWorkerService(
                reviewRepository,
                promptVersionRepository,
                postRepository,
                aiReviewGenerator,
                requestFactory,
                transactionManager,
                clock,
                properties.leaseDuration(),
                properties.maxClaims(),
                new AiReviewRetryBackoff(() -> ThreadLocalRandom.current().nextDouble())
        );
    }

    // 스케줄링을 끈 환경(테스트)에서는 폴러를 만들지 않고 서비스 메서드를 직접 부른다.
    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "chalkak.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AiReviewWorker aiReviewWorker(
            AiReviewWorkerService service,
            AiReviewWorkerProperties properties
    ) {
        return new AiReviewWorker(
                service,
                properties.concurrency(),
                SHUTDOWN_GRACE
        );
    }

    // 임대가 호출 제한 시간보다 짧으면 호출이 끝나기 전에 다른 워커가 같은 행을 다시 가져가 OpenAI를 두 번 호출한다.
    private void validateLease(Duration leaseDuration, OpenAiProperties openAiProperties) {
        Duration minimum = openAiProperties.connectTimeout()
                .plus(openAiProperties.readTimeout())
                .plus(LEASE_MARGIN);
        if (leaseDuration.compareTo(minimum) < 0) {
            throw new IllegalArgumentException(
                    "AI 리뷰 임대 시간(" + leaseDuration + ")은 OpenAI 연결·응답 제한 시간에 여유 "
                            + LEASE_MARGIN + "를 더한 " + minimum + " 이상이어야 합니다.");
        }
    }
}
