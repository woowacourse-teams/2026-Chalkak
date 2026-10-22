package com.chalkak.backend.analytics.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.analytics.service.UserMetricsAggregationRunner;
import java.lang.reflect.Method;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class UserMetricsAggregationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UserMetricsAggregationConfiguration.class)
            .withBean(UserMetricsAggregationRunner.class,
                    () -> mock(UserMetricsAggregationRunner.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    @DisplayName("집계가 꺼져 있거나 설정이 없으면 스케줄러와 전용 스레드를 만들지 않는다")
    void configuration_disabledOrMissing_doesNotCreateScheduler() {
        // When & Then
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(UserMetricsAggregationScheduler.class);
            assertThat(context).doesNotHaveBean("analyticsAggregationScheduler");
        });
        contextRunner.withPropertyValues("chalkak.analytics.aggregation.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(UserMetricsAggregationScheduler.class);
                    assertThat(context).doesNotHaveBean("analyticsAggregationScheduler");
                });
    }

    @Test
    @DisplayName("집계를 켜면 스케줄러와 집계 전용 단일 스레드를 만든다")
    void configuration_enabled_createsDedicatedScheduler() {
        // Given
        ApplicationContextRunner enabled = contextRunner
                .withPropertyValues("chalkak.analytics.aggregation.enabled=true");

        // When & Then
        enabled.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(UserMetricsAggregationScheduler.class);
            ThreadPoolTaskScheduler scheduler = context.getBean(
                    "analyticsAggregationScheduler", ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                    .isEqualTo(1);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("analytics-aggregation-");
        });
    }

    @Test
    @DisplayName("집계 작업은 공용 풀이 아닌 집계 전용 스케줄러 빈을 지정한다")
    void aggregateYesterday_scheduledAnnotation_targetsDedicatedScheduler() throws Exception {
        // Given
        Method method = UserMetricsAggregationScheduler.class.getMethod("aggregateYesterday");

        // When
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        // Then
        assertThat(scheduled.scheduler()).isEqualTo("analyticsAggregationScheduler");
    }
}
