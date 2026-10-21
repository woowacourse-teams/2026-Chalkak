package com.chalkak.backend.aireview.domain;

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
 * 게시물 한 건의 AI 리뷰. 게시물당 하나이며 요청 시점에 만든 프롬프트 버전을 끝까지 쓴다.
 */
@Entity
@Table(name = "ai_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiReview {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "prompt_version_id", nullable = false, updatable = false)
    private UUID promptVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempt;

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
        review.status = Status.QUEUED;
        return review;
    }

    /**
     * 워커가 이 행을 가져간다. 대기 중이거나 임대가 끝난 처리 중 행만 가져갈 수 있으며 가져갈 때마다 시도 번호가 늘고 줄지 않는다. 시도
     * 번호가 상한을 넘게 되면 같은 행이 워커를 반복해 멈추게 하는 것을 막으려고 처리하지 않고 실패로 끝낸다(시도 번호는 늘리지 않는다).
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
        if (attempt + 1 > maxClaims) {
            finish(
                    Status.FAILED,
                    FailureReason.LEASE_EXHAUSTED.name(),
                    now
            );
            return false;
        }
        attempt++;
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
     * 시도 번호가 현재와 같고 처리 중일 때만 실패를 반영한다.
     *
     * @return 반영했으면 true, 늦은 워커라 반영하지 않았으면 false
     */
    public boolean fail(
            int attempt,
            String errorCode,
            Invocation invocation,
            Instant now
    ) {
        if (!isProcessingAttempt(attempt)) {
            return false;
        }
        this.invocation = invocation;
        finish(
                Status.FAILED,
                errorCode,
                now
        );
        return true;
    }

    private boolean isClaimableAt(Instant now) {
        return status == Status.QUEUED
                || (status == Status.PROCESSING && leaseExpiresAt.isBefore(now));
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
        POST_UNAVAILABLE, PROMPT_VERSION_UNAVAILABLE, LEASE_EXHAUSTED, UNEXPECTED_ERROR
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
