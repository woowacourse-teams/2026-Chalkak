package com.chalkak.backend.analytics.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.analytics.service.UserMetricsAggregationRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UserMetricsAggregationSchedulerTest {

    private final UserMetricsAggregationRunner aggregationRunner =
            mock(UserMetricsAggregationRunner.class);

    @Test
    @DisplayName("집계 작업은 KST 기준 어제 날짜를 집계한다")
    void aggregateYesterday_fixedClock_aggregatesYesterdayInKst() {
        // Given
        // UTC 2030-01-02 16:30은 KST 2030-01-03 01:30이다.
        Clock clock = Clock.fixed(Instant.parse("2030-01-02T16:30:00Z"), ZoneOffset.UTC);
        UserMetricsAggregationScheduler scheduler =
                new UserMetricsAggregationScheduler(aggregationRunner, clock);

        // When
        scheduler.aggregateYesterday();

        // Then
        verify(aggregationRunner).run(LocalDate.of(2030, 1, 2));
    }

    @Test
    @DisplayName("enabled를 true로 켠 경우에만 집계 스케줄러 빈이 만들어진다")
    void scheduler_enabledProperty_controlsBeanExistence() {
        // Given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(UserMetricsAggregationScheduler.class)
                .withBean(UserMetricsAggregationRunner.class, () -> aggregationRunner)
                .withBean(Clock.class, Clock::systemUTC);

        // When & Then
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(UserMetricsAggregationScheduler.class));
        contextRunner.withPropertyValues("chalkak.analytics.aggregation.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(UserMetricsAggregationScheduler.class));
        contextRunner.withPropertyValues("chalkak.analytics.aggregation.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(UserMetricsAggregationScheduler.class));
    }
}
