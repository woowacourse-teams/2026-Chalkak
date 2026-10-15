package com.chalkak.backend.analytics.service;

import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 사용자 지표 집계를 한 번에 하나만 실행한다.
 *
 * <p>
 * 스케줄러(동기)와 관리자 수동 요청(백그라운드)이 같은 가드를 쓰므로 두 실행이 겹치지 않는다. 실패 로그는 두 경로가 같은 모양으로
 * 남도록 여기서 남긴다. 백그라운드 실행의 실패는 로그만 남고 호출자에게 전달되지 않는다.
 */
@Slf4j
@Service
public class UserMetricsAggregationRunner {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserMetricsAggregationService aggregationService;
    private final TaskExecutor taskExecutor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public UserMetricsAggregationRunner(
            UserMetricsAggregationService aggregationService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            Clock clock
    ) {
        this.aggregationService = aggregationService;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    /** 끝난 날(KST)만 받아 백그라운드로 집계를 시작한다. 이미 집계 중이면 시작하지 않는다. */
    public void start(UUID adminId, LocalDate metricDate) {
        validateFinishedDay(metricDate);
        if (!running.compareAndSet(false, true)) {
            throw inProgress();
        }
        log.atInfo()
                .addKeyValue(LogFields.ADMIN_ID, adminId.toString())
                .addKeyValue(LogFields.METRIC_DATE, metricDate.toString())
                .log("사용자 지표 수동 집계 요청");
        try {
            taskExecutor.execute(() -> {
                try {
                    aggregationService.aggregate(metricDate);
                } catch (RuntimeException e) {
                    // 받을 호출자가 없으니 로그로만 알린다. 실행기 스레드로 던지면 같은 오류가 한 번 더 찍힌다.
                    logFailure(metricDate, e);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    /**
     * 호출 스레드에서 집계한다. 실패하면 로그를 남기고 예외를 그대로 던진다.
     *
     * <p>
     * 수동 집계와 겹쳐 실행하지 못한 경우도 그날 집계가 빠진 것이므로 실패로 남긴다.
     */
    public void run(LocalDate metricDate) {
        if (!running.compareAndSet(false, true)) {
            BusinessException e = inProgress();
            logFailure(metricDate, e);
            throw e;
        }
        try {
            aggregationService.aggregate(metricDate);
        } catch (RuntimeException e) {
            logFailure(metricDate, e);
            throw e;
        } finally {
            running.set(false);
        }
    }

    private void validateFinishedDay(LocalDate metricDate) {
        if (metricDate.isBefore(LocalDate.now(clock.withZone(KST)))) {
            return;
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "아직 끝나지 않은 날짜는 집계할 수 없습니다.");
    }

    private BusinessException inProgress() {
        return new BusinessException(
                ErrorCode.USER_METRICS_AGGREGATION_IN_PROGRESS,
                "이미 사용자 지표 집계가 진행 중입니다.");
    }

    private void logFailure(
            LocalDate metricDate,
            RuntimeException cause
    ) {
        log.atError()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_ERROR)
                .addKeyValue(LogFields.METRIC_DATE, metricDate.toString())
                .setCause(cause)
                .log("사용자 지표 집계 실패");
    }
}
