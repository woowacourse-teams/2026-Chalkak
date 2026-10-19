package com.chalkak.backend.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class UserMetricsAggregationRunnerTest {

    private static final UUID ADMIN_ID = UUID.fromString("0198fd21-0000-7000-8000-000000000001");
    // UTC 2030-01-02 16:30은 KST 2030-01-03 01:30이다.
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2030-01-02T16:30:00Z"), ZoneOffset.UTC);
    private static final LocalDate YESTERDAY = LocalDate.of(2030, 1, 2);

    private final UserMetricsAggregationService aggregationService =
            mock(UserMetricsAggregationService.class);
    // 바로 실행하지 않고 쌓아 두어 "진행 중" 상태를 만든다.
    private final List<Runnable> queuedTasks = new ArrayList<>();
    private final UserMetricsAggregationRunner runner =
            new UserMetricsAggregationRunner(aggregationService, queuedTasks::add, CLOCK);
    private final Logger logger =
            (Logger) LoggerFactory.getLogger(UserMetricsAggregationRunner.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("백그라운드 집계를 시작하면 요청 시점에는 집계하지 않고 실행기가 돌릴 때 해당 날짜를 집계한다")
    void start_finishedDay_aggregatesInExecutor() {
        // When
        runner.start(ADMIN_ID, YESTERDAY);

        // Then
        verify(aggregationService, never()).aggregate(any());
        queuedTasks.getFirst().run();
        verify(aggregationService).aggregate(YESTERDAY);
    }

    @Test
    @DisplayName("집계 요청은 관리자와 날짜를 INFO 로그로 남긴다")
    void start_finishedDay_logsAdminAndMetricDate() {
        // When
        runner.start(ADMIN_ID, YESTERDAY);

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getKeyValuePairs())
                .anyMatch(pair -> pair.key.equals("adminId")
                        && pair.value.equals(ADMIN_ID.toString()))
                .anyMatch(pair -> pair.key.equals("metricDate")
                        && pair.value.equals("2030-01-02"));
    }

    @Test
    @DisplayName("집계가 진행 중일 때 다시 시작하면 진행 중 에러 코드로 거절하고 두 번 집계하지 않는다")
    void start_alreadyRunning_throwsInProgressWithoutSecondAggregation() {
        // Given
        runner.start(ADMIN_ID, YESTERDAY);

        // When & Then
        assertThatThrownBy(() -> runner.start(ADMIN_ID, YESTERDAY))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.USER_METRICS_AGGREGATION_IN_PROGRESS));
        assertThat(queuedTasks).hasSize(1);
        queuedTasks.getFirst().run();
        verify(aggregationService, times(1)).aggregate(any());
    }

    @Test
    @DisplayName("집계가 성공하면 다시 시작할 수 있다")
    void start_afterSuccess_releasesGuard() {
        // Given
        runner.start(ADMIN_ID, YESTERDAY);
        queuedTasks.getFirst().run();

        // When
        runner.start(ADMIN_ID, YESTERDAY);

        // Then
        assertThat(queuedTasks).hasSize(2);
    }

    @Test
    @DisplayName("백그라운드 집계가 실패하면 실행기로 던지지 않고 type=error 로그를 남기며 다시 시작할 수 있다")
    void start_afterFailure_logsErrorAndReleasesGuard() {
        // Given
        IllegalStateException failure = new IllegalStateException("db down");
        doThrow(failure).when(aggregationService).aggregate(any());
        runner.start(ADMIN_ID, YESTERDAY);

        // When
        queuedTasks.getFirst().run();

        // Then
        ILoggingEvent event = appender.list.getLast();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).isEqualTo("사용자 지표 집계 실패");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("db down");
        assertThat(event.getKeyValuePairs())
                .anyMatch(pair -> pair.key.equals("type") && pair.value.equals("error"))
                .anyMatch(pair -> pair.key.equals("metricDate")
                        && pair.value.equals("2030-01-02"));
        runner.start(ADMIN_ID, YESTERDAY);
        assertThat(queuedTasks).hasSize(2);
    }

    @Test
    @DisplayName("실행기가 작업을 거절하면 예외를 던지고 다시 시작할 수 있다")
    void start_executorRejects_rethrowsAndReleasesGuard() {
        // Given
        TaskRejectedException rejected = new TaskRejectedException("queue full");
        TaskExecutor rejectingExecutor = task -> {
            throw rejected;
        };
        UserMetricsAggregationRunner rejectingRunner =
                new UserMetricsAggregationRunner(aggregationService, rejectingExecutor, CLOCK);

        // When & Then
        assertThatThrownBy(() -> rejectingRunner.start(ADMIN_ID, YESTERDAY)).isSameAs(rejected);
        assertThatThrownBy(() -> rejectingRunner.start(ADMIN_ID, YESTERDAY)).isSameAs(rejected);
    }

    @Test
    @DisplayName("오늘과 미래 날짜는 거절하고 어제는 허용한다")
    void start_todayAndFuture_throwsBusinessError() {
        // When & Then
        assertThatThrownBy(() -> runner.start(ADMIN_ID, LocalDate.of(2030, 1, 3)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR));
        assertThatThrownBy(() -> runner.start(ADMIN_ID, LocalDate.of(2030, 1, 4)))
                .isInstanceOf(BusinessException.class);
        runner.start(ADMIN_ID, YESTERDAY);
        assertThat(queuedTasks).hasSize(1);
    }

    @Test
    @DisplayName("거절된 날짜 요청은 진행 중 표시를 남기지 않는다")
    void start_futureDate_doesNotHoldGuard() {
        // Given
        assertThatThrownBy(() -> runner.start(ADMIN_ID, LocalDate.of(2030, 1, 3)))
                .isInstanceOf(BusinessException.class);

        // When
        runner.start(ADMIN_ID, YESTERDAY);

        // Then
        assertThat(queuedTasks).hasSize(1);
    }

    @Test
    @DisplayName("동기 집계는 호출 스레드에서 해당 날짜를 집계하고 끝나면 다시 실행할 수 있다")
    void run_idle_aggregatesAndReleasesGuard() {
        // When
        runner.run(YESTERDAY);
        runner.run(YESTERDAY);

        // Then
        verify(aggregationService, times(2)).aggregate(YESTERDAY);
    }

    @Test
    @DisplayName("동기 집계가 실패하면 type=error 로그를 남기고 예외를 그대로 던진다")
    void run_aggregationFails_logsErrorAndRethrows() {
        // Given
        IllegalStateException failure = new IllegalStateException("db down");
        doThrow(failure).when(aggregationService).aggregate(any());

        // When & Then
        assertThatThrownBy(() -> runner.run(YESTERDAY)).isSameAs(failure);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getKeyValuePairs())
                .anyMatch(pair -> pair.key.equals("type") && pair.value.equals("error"))
                .anyMatch(pair -> pair.key.equals("metricDate")
                        && pair.value.equals("2030-01-02"));
        assertThatThrownBy(() -> runner.run(YESTERDAY)).isSameAs(failure);
    }

    @Test
    @DisplayName("백그라운드 집계가 진행 중일 때 동기 집계를 하면 type=error 로그를 남기고 진행 중 에러 코드로 거절한다")
    void run_whileBackgroundRunning_logsErrorAndThrowsInProgress() {
        // Given
        runner.start(ADMIN_ID, YESTERDAY);
        appender.list.clear();

        // When & Then
        assertThatThrownBy(() -> runner.run(YESTERDAY))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.USER_METRICS_AGGREGATION_IN_PROGRESS));
        verify(aggregationService, never()).aggregate(any());
        assertThat(appender.list).singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR))
                .satisfies(event -> assertThat(event.getKeyValuePairs())
                        .anyMatch(pair -> pair.key.equals("type") && pair.value.equals("error")));
    }

    @Test
    @DisplayName("두 요청이 동시에 시작하면 하나만 집계하고 나머지는 진행 중 에러 코드로 거절한다")
    void start_concurrentRequests_onlyOneAggregates() throws Exception {
        // Given
        int requestCount = 8;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();
        List<Runnable> tasks = Collections.synchronizedList(new ArrayList<>());
        UserMetricsAggregationRunner concurrentRunner =
                new UserMetricsAggregationRunner(aggregationService, tasks::add, CLOCK);
        ExecutorService requests = Executors.newFixedThreadPool(requestCount);

        // When
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            futures.add(requests.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    concurrentRunner.start(ADMIN_ID, YESTERDAY);
                } catch (BusinessException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        requests.shutdown();

        // Then
        assertThat(tasks).hasSize(1);
        assertThat(rejected).hasValue(requestCount - 1);
    }
}
