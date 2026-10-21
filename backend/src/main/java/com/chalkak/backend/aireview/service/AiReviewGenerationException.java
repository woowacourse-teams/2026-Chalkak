package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;

/**
 * AI 리뷰 생성 실패. 메시지에는 실패 유형만 담아 API 키·프롬프트·응답 본문이 새지 않게 한다.
 */
public class AiReviewGenerationException extends BusinessException {

    private final FailureType failureType;
    private final Attempt attempt;
    private final Duration retryAfter;

    /**
     * 실패 유형만 담는다.
     */
    public static AiReviewGenerationException of(FailureType failureType) {
        return new AiReviewGenerationException(
                failureType,
                null,
                null
        );
    }

    /**
     * 응답 본문을 받은 뒤의 실패. 사용량·지연시간·응답 ID를 함께 담는다.
     */
    public static AiReviewGenerationException withAttempt(
            FailureType failureType,
            Attempt attempt
    ) {
        return new AiReviewGenerationException(
                failureType,
                attempt,
                null
        );
    }

    /**
     * 외부 서비스가 알려 준 최소 대기 시간을 함께 담는다.
     */
    public static AiReviewGenerationException withRetryAfter(
            FailureType failureType,
            Duration retryAfter
    ) {
        return new AiReviewGenerationException(
                failureType,
                null,
                retryAfter
        );
    }

    /**
     * 원인 예외를 보존한다. 메시지에는 원인 내용을 싣지 않는다.
     */
    public static AiReviewGenerationException withCause(
            FailureType failureType,
            Throwable cause
    ) {
        AiReviewGenerationException exception = of(failureType);
        exception.initCause(cause);
        return exception;
    }

    private AiReviewGenerationException(
            FailureType failureType,
            Attempt attempt,
            Duration retryAfter
    ) {
        super(ErrorCode.BUSINESS_ERROR, "AI 리뷰 생성에 실패했습니다. 유형: " + failureType);
        this.failureType = failureType;
        this.attempt = attempt;
        this.retryAfter = retryAfter;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    /**
     * 응답 본문을 받은 뒤 실패했을 때만 값이 있다. HTTP 오류나 시간 초과는 비어 있다.
     */
    public Optional<Attempt> findAttempt() {
        return Optional.ofNullable(attempt);
    }

    /**
     * 외부 서비스가 알려 준 최소 대기 시간. HTTP 오류 응답에 대기 시간 힌트가 있을 때만 값이 있다.
     */
    public Optional<Duration> findRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /**
     * AI 리뷰 생성 실패 유형. 같은 요청을 다시 보내면 성공할 수 있는 실패만 재시도 대상이다. 결제·사용 한도 소진은 기다려도 풀리지 않아
     * 재시도하지 않는다.
     */
    public enum FailureType {
        TIMEOUT(true), RATE_LIMITED(true), QUOTA_EXCEEDED(false), UPSTREAM_ERROR(true), BAD_REQUEST(
                false), REFUSED(false), INCOMPLETE(false), INVALID_OUTPUT(false);

        private final boolean retryable;

        FailureType(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
