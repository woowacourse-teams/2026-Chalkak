package com.chalkak.backend.analytics.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.analytics.service.UserMetricsAggregationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UserMetricsAggregationSchedulerTest {

    private final UserMetricsAggregationService aggregationService =
            mock(UserMetricsAggregationService.class);

    @Test
    @DisplayName("집계 작업은 KST 기준 어제 날짜를 집계한다")
    void aggregateYesterday_fixedClock_aggregatesYesterdayInKst() {
        // Given
        // UTC 2030-01-02 16:30은 KST 2030-01-03 01:30이다.
        Clock clock = Clock.fixed(Instant.parse("2030-01-02T16:30:00Z"), ZoneOffset.UTC);
        UserMetricsAggregationScheduler scheduler =
                new UserMetricsAggregationScheduler(aggregationService, clock);

        // When
        scheduler.aggregateYesterday();

        // Then
        verify(aggregationService).aggregate(LocalDate.of(2030, 1, 2));
    }

    @Test
    @DisplayName("집계가 실패하면 type=error 로그를 남기고 예외를 그대로 던진다")
    void aggregateYesterday_aggregationFails_logsErrorAndRethrows() {
        // Given
        Clock clock = Clock.fixed(Instant.parse("2030-01-02T16:30:00Z"), ZoneOffset.UTC);
        UserMetricsAggregationScheduler scheduler =
                new UserMetricsAggregationScheduler(aggregationService, clock);
        IllegalStateException failure = new IllegalStateException("db down");
        doThrow(failure).when(aggregationService).aggregate(any());
        Logger logger = (Logger) LoggerFactory.getLogger(UserMetricsAggregationScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            // When & Then
            assertThatThrownBy(scheduler::aggregateYesterday).isSameAs(failure);
            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getKeyValuePairs())
                    .anyMatch(pair -> pair.key.equals("type") && pair.value.equals("error"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("enabled를 true로 켠 경우에만 집계 스케줄러 빈이 만들어진다")
    void scheduler_enabledProperty_controlsBeanExistence() {
        // Given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(UserMetricsAggregationScheduler.class)
                .withBean(UserMetricsAggregationService.class, () -> aggregationService)
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
