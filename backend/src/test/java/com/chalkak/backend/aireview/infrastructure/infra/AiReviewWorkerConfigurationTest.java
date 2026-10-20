package com.chalkak.backend.aireview.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.aireview.infrastructure.infra.openai.OpenAiProperties;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.aireview.repository.AiReviewRepository;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequestFactory;
import com.chalkak.backend.aireview.service.AiReviewGenerator;
import com.chalkak.backend.aireview.service.AiReviewWorkerService;
import com.chalkak.backend.post.repository.PostRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

class AiReviewWorkerConfigurationTest {

    private static final String ENABLED = "chalkak.ai-review.worker.enabled=true";
    // application.yml이 정하는 값. 이 테스트는 컨텍스트에 yml을 싣지 않으므로 직접 넣는다.
    private static final String[] YAML_VALUES = {
            "chalkak.ai-review.worker.concurrency=10",
            "chalkak.ai-review.worker.lease-duration=120s",
            "chalkak.ai-review.worker.max-claims=3"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiReviewWorkerConfiguration.class)
            .withBean(OpenAiProperties.class, () -> new OpenAiProperties(
                    "test-key",
                    URI.create("https://api.openai.test/v1"),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(60),
                    List.of("gpt-6.1-sol")
            ))
            .withBean(AiReviewRepository.class, () -> mock(AiReviewRepository.class))
            .withBean(AiPromptVersionRepository.class,
                    () -> mock(AiPromptVersionRepository.class))
            .withBean(PostRepository.class, () -> mock(PostRepository.class))
            .withBean(AiReviewGenerator.class, () -> mock(AiReviewGenerator.class))
            .withBean(AiReviewGenerationRequestFactory.class,
                    () -> mock(AiReviewGenerationRequestFactory.class))
            .withBean(PlatformTransactionManager.class,
                    () -> mock(PlatformTransactionManager.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    @DisplayName("워커를 켜지 않으면 워커와 전용 스케줄러를 만들지 않는다")
    void configuration_disabled_createsNothing() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.ai-review.worker.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AiReviewWorkerService.class);
                    assertThat(context).doesNotHaveBean(AiReviewWorker.class);
                    assertThat(context).doesNotHaveBean("aiReviewWorkerScheduler");
                });
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(AiReviewWorker.class));
    }

    @Test
    @DisplayName("워커를 켜면 폴러 전용 단일 스레드 스케줄러와 설정값으로 워커를 만든다")
    void configuration_enabled_createsWorker() {
        // When & Then
        contextRunner.withPropertyValues(ENABLED).withPropertyValues(YAML_VALUES).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AiReviewWorkerService.class);
            assertThat(context).hasSingleBean(AiReviewWorker.class);
            ThreadPoolTaskScheduler scheduler = context.getBean("aiReviewWorkerScheduler",
                    ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("ai-review-poll-");
            AiReviewWorkerProperties properties = context.getBean(AiReviewWorkerProperties.class);
            assertThat(properties.concurrency()).isEqualTo(10);
            assertThat(properties.leaseDuration()).isEqualTo(Duration.ofSeconds(120));
            assertThat(properties.maxClaims()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("임대 시간이 연결+응답 제한 시간+20초보다 짧으면 시작하지 못한다")
    void configuration_leaseShorterThanCallTimeouts_failsFast() {
        // When & Then
        enabledRunner("chalkak.ai-review.worker.lease-duration=84s").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("임대 시간");
        });
        enabledRunner("chalkak.ai-review.worker.lease-duration=85s")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "chalkak.ai-review.worker.concurrency=0",
            "chalkak.ai-review.worker.max-claims=0"
    })
    @DisplayName("동시 처리 수와 가져가기 상한은 1 이상이어야 한다")
    void configuration_nonPositiveLimits_failsFast(String property) {
        // When & Then
        enabledRunner(property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("스케줄링을 끄면 폴러는 만들지 않고 처리 서비스만 남긴다")
    void configuration_schedulingDisabled_doesNotCreatePoller() {
        // When & Then
        enabledRunner("chalkak.scheduling.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AiReviewWorkerService.class);
            assertThat(context).doesNotHaveBean(AiReviewWorker.class);
        });
    }

    private ApplicationContextRunner enabledRunner(String property) {
        return contextRunner.withPropertyValues(ENABLED)
                .withPropertyValues(YAML_VALUES)
                .withPropertyValues(property);
    }
}
