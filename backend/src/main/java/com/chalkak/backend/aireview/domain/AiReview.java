package com.chalkak.backend.aireview.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 게시물 한 건의 AI 리뷰. 게시물당 하나이며 사용자가 요청할 때 고른 프롬프트 버전을 그 요청이 끝날 때까지 쓴다. 일시적 실패는 같은
 * 행을 대기로 되돌려 자동으로 다시 시도하고, 실패한 리뷰는 사용자가 정해진 횟수까지 다시 요청할 수 있다. 다시 요청하면 같은 행을 대기로
 * 되돌리고 그때의 최신 버전으로 다시 고정한다.
 */
@Entity
@Table(name = "ai_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiReview {

    private static final int MAX_REQUEST_COUNT = 3;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * 이번 회차에 쓰는 프롬프트 버전. 사용자가 요청하거나 다시 요청할 때 고정하며 회차가 끝날 때까지 바뀌지 않는다.
     */
    @Column(name = "prompt_version_id", nullable = false)
    private UUID promptVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /**
     * 워커가 가져갈 때마다 늘고 줄지 않는다. 늦게 끝난 워커의 반영을 막는 기준이다.
     */
    @Column(nullable = false)
    private int attempt;

    /**
     * 이번 회차(사용자 요청 1회) 안에서 가져간 횟수. 재시도와 임대 회수를 모두 세며 자동 시도 상한은 이 값으로 비교한다.
     */
    @Column(name = "round_attempt", nullable = false)
    private int roundAttempt;

    /**
     * 사용자 요청 횟수(처음 요청 포함). 상한은 3번이다.
     */
    @Column(name = "request_count", nullable = false)
    private int requestCount;

    /**
     * 자동 재시도로 대기 중일 때 다시 가져갈 수 있는 시각. 대기 중이 아니면 null이다.
     */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private AiReviewResult result;

    /**
     * 모델이 돌려준 output_text를 검증·재해석하지 않고 담는다. jsonb라 공백과 키 순서는 정규화되지만 값은 바뀌지 않는다.
     * Hibernate의 JSON 변환기가 Jackson 3의 JsonNode를 모르므로 문자열로 다룬다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String output;

    @Embedded
    private Invocation invocation;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    /**
     * 이번 회차의 사용자 요청 시각. 처음 요청 시각은 {@link #requestedAt}에 남는다.
     */
    @Column(name = "last_requested_at", nullable = false)
    private Instant lastRequestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static AiReview request(
            UUID postId,
            UUID userId,
            UUID promptVersionId,
            UUID requestId,
            Instant requestedAt
    ) {
        AiReview review = new AiReview();
        review.postId = postId;
        review.userId = userId;
        review.promptVersionId = promptVersionId;
        review.requestId = requestId;
        review.requestedAt = requestedAt;
        review.lastRequestedAt = requestedAt;
        review.requestCount = 1;
        review.status = Status.QUEUED;
        return review;
    }

    /**
     * 워커가 이 행을 가져간다. 대기 중(다음 시도 시각이 지난 것)이거나 임대가 끝난 처리 중 행만 가져갈 수 있으며 가져갈 때마다 시도
     * 번호와 회차 시도 횟수가 늘고 줄지 않는다. 회차 시도 횟수가 상한을 넘게 되면 같은 행이 워커를 반복해 멈추게 하는 것을 막으려고
     * 처리하지 않고 실패로 끝낸다(시도 번호와 회차 시도 횟수는 늘리지 않는다). 마지막 시도는 결과를 모르는 중단이므로 이전 시도의 사용량과
     * 응답 ID는 비운다.
     *
     * @return 처리해야 하면 true, 상한을 넘어 실패로 끝냈으면 false
     */
    public boolean claim(
            Instant now,
            Instant leaseExpiresAt,
            int maxClaims
    ) {
        if (!isClaimableAt(now)) {
            throw new IllegalStateException("가져갈 수 없는 AI 리뷰입니다. 상태: " + status);
        }
        if (roundAttempt + 1 > maxClaims) {
            invocation = null;
            finish(
                    Status.FAILED,
                    FailureReason.LEASE_EXHAUSTED.name(),
                    now
            );
            return false;
        }
        attempt++;
        roundAttempt++;
        nextAttemptAt = null;
        status = Status.PROCESSING;
        this.leaseExpiresAt = leaseExpiresAt;
        if (startedAt == null) {
            startedAt = now;
        }
        return true;
    }

    /**
     * 시도 번호가 현재와 같고 처리 중일 때만 결과를 반영한다. 임대가 끝나 다른 워커가 다시 가져간 뒤 늦게 끝난 워커의 결과는 버린다.
     *
     * @return 반영했으면 true, 늦은 워커라 반영하지 않았으면 false
     */
    public boolean complete(
            int attempt,
            AiReviewResult result,
            String output,
            Invocation invocation,
            Instant now
    ) {
        if (!isProcessingAttempt(attempt)) {
            return false;
        }
        this.result = result;
        this.output = output;
        this.invocation = invocation;
        finish(
                Status.COMPLETED,
                null,
                now
        );
        return true;
    }

    /**
     * 시도 번호가 현재와 같고 처리 중일 때만 실패를 반영한다. 재시도할 수 있는 실패이고 이번 회차의 시도가 남았으면 대기로 되돌려 다음 시도
     * 시각을 정한다. 이때 오류 코드와 사용량은 마지막 시도의 값이며 완료 시각은 비워 둔다. 그 밖에는 실패로 끝낸다.
     *
     * @return 반영했으면 true, 늦은 워커라 반영하지 않았으면 false
     */
    public boolean failOrRetry(
            int attempt,
            String errorCode,
            boolean retryable,
            Invocation invocation,
            Instant nextAttemptAt,
            int maxClaims,
            Instant now
    ) {
        if (!isProcessingAttempt(attempt)) {
            return false;
        }
        this.invocation = invocation;
        if (retryable && roundAttempt < maxClaims) {
            status = Status.QUEUED;
            this.errorCode = errorCode;
            this.nextAttemptAt = nextAttemptAt;
            leaseExpiresAt = null;
            return true;
        }
        finish(
                Status.FAILED,
                errorCode,
                now
        );
        return true;
    }

    /**
     * 실패한 리뷰를 사용자가 다시 요청해 대기로 되돌린다. 요청 횟수가 늘고 회차 시도 횟수는 0으로 돌아가며 프롬프트 버전을 다시 고정한다.
     * 이전 회차의 시작·완료 시각, 오류 코드, 다음 시도 시각, 사용량·지연·응답 ID는 회차가 섞이지 않게 비운다. 시도 번호는 늦은 워커를
     * 막는 기준이라 그대로 두고, 최초 요청 ID와 시각도 바꾸지 않는다.
     */
    public void requestAgain(UUID promptVersionId, Instant now) {
        if (status != Status.FAILED) {
            throw new IllegalStateException("실패한 AI 리뷰만 다시 요청할 수 있습니다. 상태: " + status);
        }
        validateRequestsLeft();
        this.promptVersionId = promptVersionId;
        requestCount++;
        lastRequestedAt = now;
        roundAttempt = 0;
        status = Status.QUEUED;
        startedAt = null;
        completedAt = null;
        errorCode = null;
        nextAttemptAt = null;
        leaseExpiresAt = null;
        invocation = null;
    }

    /**
     * 실패한 리뷰이고 사용자 요청 횟수가 상한에 닿지 않았으면 다시 요청할 수 있다.
     */
    public boolean canRequestAgain() {
        return status == Status.FAILED && requestCount < MAX_REQUEST_COUNT;
    }

    /**
     * 사용자 요청 횟수가 상한에 닿았으면 사용자에게 알린다.
     */
    public void validateRequestsLeft() {
        if (requestCount >= MAX_REQUEST_COUNT) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "더 이상 AI 리뷰를 다시 요청할 수 없습니다.");
        }
    }

    /**
     * 사용자에게 결과를 알릴 최종 상태인지. 완료와 실패가 대상이고, 게시물을 쓸 수 없어 실패한 경우는 사용자가 할 수 있는 일이 없어
     * 제외한다. 재시도 대기와 처리 중은 아직 끝나지 않았으므로 대상이 아니다.
     */
    public boolean isResultNotifiable() {
        if (isCompleted()) {
            return true;
        }
        return isFailed() && !FailureReason.POST_UNAVAILABLE.matches(errorCode);
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    /**
     * 실패한 시도를 자동으로 다시 시도하려고 대기 중인지. 앱에는 이 상태도 처리 중으로 보인다.
     */
    public boolean isRetryScheduled() {
        return status == Status.QUEUED && nextAttemptAt != null;
    }

    private boolean isClaimableAt(Instant now) {
        if (status == Status.QUEUED) {
            return nextAttemptAt == null || !nextAttemptAt.isAfter(now);
        }
        return status == Status.PROCESSING && leaseExpiresAt.isBefore(now);
    }

    private boolean isProcessingAttempt(int attempt) {
        return status == Status.PROCESSING && this.attempt == attempt;
    }

    private void finish(
            Status terminalStatus,
            String errorCode,
            Instant now
    ) {
        status = terminalStatus;
        this.errorCode = errorCode;
        leaseExpiresAt = null;
        nextAttemptAt = null;
        completedAt = now;
    }

    /**
     * 호출 기록이 없으면 {@link Invocation#NONE}을 돌려준다. 컬럼이 모두 null인 행은 Hibernate가 null로
     * 읽는다.
     */
    public Invocation getInvocation() {
        if (invocation == null) {
            return Invocation.NONE;
        }
        return invocation;
    }

    /**
     * 서버 안에서 쓰는 처리 단계.
     */
    public enum Status {
        QUEUED, PROCESSING, COMPLETED, FAILED
    }

    /**
     * 워커가 직접 정하는 실패 사유. 생성기 실패는 생성기의 실패 유형 이름을 그대로 오류 코드로 저장한다.
     */
    public enum FailureReason {
        POST_UNAVAILABLE, PROMPT_VERSION_UNAVAILABLE, LEASE_EXHAUSTED, UNEXPECTED_ERROR;

        /**
         * 저장된 errorCode가 이 사유인지. errorCode 컬럼은 호출 실패 종류까지 담는 문자열이라 이름으로 비교한다.
         */
        public boolean matches(String errorCode) {
            return name().equals(errorCode);
        }
    }

    /**
     * OpenAI 호출에서 받은 사용량·지연시간·응답 ID. 응답 본문을 받지 못했거나 OpenAI가 주지 않은 값은 null이다.
     */
    @Embeddable
    public record Invocation(
            @Column(name = "input_tokens") Integer inputTokens,
            @Column(name = "cached_input_tokens") Integer cachedInputTokens,
            @Column(name = "output_tokens") Integer outputTokens,
            @Column(name = "reasoning_tokens") Integer reasoningTokens,
            @Column(name = "latency_ms") Long latencyMs,
            @Column(name = "openai_response_id", length = 100) String openaiResponseId
    ) {

        public static final Invocation NONE = new Invocation(
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
