package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationDispatch;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

public class NotificationOutboxWorker {

    private static final String SENDER_ERROR_CODE = "NOTIFICATION_SENDER_ERROR";
    private static final String RETRYABLE_FAILURE_CODE = "NOTIFICATION_RETRYABLE_FAILURE";
    private static final String PERMANENT_FAILURE_CODE = "NOTIFICATION_PERMANENT_FAILURE";
    private static final String MAX_ATTEMPTS_EXCEEDED_CODE =
            "NOTIFICATION_MAX_ATTEMPTS_EXCEEDED";
    private static final Pattern SAFE_FAILURE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,99}");

    private final NotificationOutboxClaimService claimService;
    private final NotificationOutboxStatusService statusService;
    private final NotificationMessageFactory messageFactory;
    private final NotificationDispatcher dispatcher;
    private final NotificationRetryPolicy retryPolicy;
    private final Clock clock;
    private final int batchSize;

    public NotificationOutboxWorker(
            NotificationOutboxClaimService claimService,
            NotificationOutboxStatusService statusService,
            NotificationMessageFactory messageFactory,
            NotificationDispatcher dispatcher,
            NotificationRetryPolicy retryPolicy,
            Clock clock,
            int batchSize
    ) {
        validateBatchSize(batchSize);
        this.claimService = claimService;
        this.statusService = statusService;
        this.messageFactory = messageFactory;
        this.dispatcher = dispatcher;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * 선점과 상태 기록은 각각 별도 서비스의 짧은 트랜잭션에서 수행한다. 외부 발송은 이 메서드의
     * 비트랜잭션 구간에서 수행해 느린 Slack 응답 동안 DB 연결과 행 잠금을 점유하지 않는다.
     */
    public int processDueBatch() {
        int processedCount = 0;
        while (processedCount < batchSize) {
            Optional<NotificationDispatch> dispatch = claimService.claimNext();
            if (dispatch.isEmpty()) {
                return processedCount;
            }
            processDispatch(dispatch.get());
            processedCount += 1;
        }
        return processedCount;
    }

    private void processDispatch(NotificationDispatch dispatch) {
        if (!retryPolicy.canAttempt(dispatch.attemptCount())) {
            statusService.markFailed(dispatch, MAX_ATTEMPTS_EXCEEDED_CODE);
            return;
        }
        NotificationMessage message = messageFactory.create(dispatch);
        NotificationSendResult result = dispatchSafely(dispatch, message);
        if (result.outcome() == NotificationSendOutcome.SENT) {
            statusService.markSent(dispatch);
            return;
        }
        if (result.outcome() == NotificationSendOutcome.PERMANENT_FAILURE) {
            statusService.markFailed(
                    dispatch,
                    normalizeFailureCode(result.failureCode(), PERMANENT_FAILURE_CODE)
            );
            return;
        }
        processRetryableFailure(dispatch, result);
    }

    private NotificationSendResult dispatchSafely(
            NotificationDispatch dispatch,
            NotificationMessage message
    ) {
        try {
            NotificationSendResult result = dispatcher.dispatch(dispatch.channel(), message);
            if (result == null || result.outcome() == null) {
                return NotificationSendResult.retryable(null, SENDER_ERROR_CODE);
            }
            return result;
        } catch (RuntimeException exception) {
            return NotificationSendResult.retryable(null, SENDER_ERROR_CODE);
        }
    }

    private void processRetryableFailure(
            NotificationDispatch dispatch,
            NotificationSendResult result
    ) {
        String failureCode = normalizeFailureCode(
                result.failureCode(),
                RETRYABLE_FAILURE_CODE
        );
        if (!retryPolicy.canRetry(dispatch.attemptCount())) {
            statusService.markFailed(dispatch, failureCode);
            return;
        }
        Duration delay = retryPolicy.calculateDelay(
                dispatch.attemptCount(),
                result.retryAfter()
        );
        statusService.markRetry(dispatch, Instant.now(clock).plus(delay), failureCode);
    }

    private String normalizeFailureCode(String failureCode, String fallback) {
        if (failureCode == null || !SAFE_FAILURE_CODE.matcher(failureCode).matches()) {
            return fallback;
        }
        return failureCode;
    }

    private void validateBatchSize(int configuredBatchSize) {
        if (configuredBatchSize <= 0) {
            throw new IllegalArgumentException("알림 워커 배치 크기는 1 이상이어야 합니다.");
        }
    }
}
