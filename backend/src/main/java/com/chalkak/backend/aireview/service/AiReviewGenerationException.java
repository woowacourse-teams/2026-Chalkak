package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.Optional;

/**
 * AI 리뷰 생성 실패. 메시지에는 실패 유형만 담아 API 키·프롬프트·응답 본문이 새지 않게 한다.
 */
public class AiReviewGenerationException extends BusinessException {

    private final FailureType failureType;
    private final Attempt attempt;

    public AiReviewGenerationException(FailureType failureType) {
        this(failureType, (Attempt) null);
    }

    public AiReviewGenerationException(FailureType failureType, Throwable cause) {
        this(failureType);
        initCause(cause);
    }

    public AiReviewGenerationException(
            FailureType failureType,
            Attempt attempt
    ) {
        super(ErrorCode.BUSINESS_ERROR, "AI 리뷰 생성에 실패했습니다. 유형: " + failureType);
        this.failureType = failureType;
        this.attempt = attempt;
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
     * AI 리뷰 생성 실패 유형. 같은 요청을 다시 보내면 성공할 수 있는 실패만 재시도 대상이다. 결제·사용 한도 소진은 기다려도 풀리지 않아 재시도하지 않는다.
     */
    public enum FailureType {
        TIMEOUT(true),
        RATE_LIMITED(true),
        QUOTA_EXCEEDED(false),
        UPSTREAM_ERROR(true),
        BAD_REQUEST(false),
        REFUSED(false),
        INCOMPLETE(false),
        INVALID_OUTPUT(false);

        private final boolean retryable;

        FailureType(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
