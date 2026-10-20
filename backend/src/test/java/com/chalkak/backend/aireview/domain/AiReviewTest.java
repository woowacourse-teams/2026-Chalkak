package com.chalkak.backend.aireview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.aireview.domain.AiReview.FailureReason;
import com.chalkak.backend.aireview.domain.AiReview.Invocation;
import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.domain.AiReviewResult.Criterion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class AiReviewTest {

    private static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000002");
    private static final UUID PROMPT_VERSION_ID = UUID
            .fromString("019a0000-0000-7000-8000-000000000003");
    private static final UUID REQUEST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000004");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-20T05:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("요청하면 대기 상태로 시작하고 처리 결과와 완료 시각은 비어 있다")
    void request_newReview_startsQueued() {
        // When
        AiReview review = AiReview.request(
                POST_ID,
                USER_ID,
                PROMPT_VERSION_ID,
                REQUEST_ID,
                REQUESTED_AT
        );

        // Then
        assertThat(review.getPostId()).isEqualTo(POST_ID);
        assertThat(review.getUserId()).isEqualTo(USER_ID);
        assertThat(review.getPromptVersionId()).isEqualTo(PROMPT_VERSION_ID);
        assertThat(review.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(review.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(review.getStatus()).isEqualTo(Status.QUEUED);
        assertThat(review.getAttempt()).isZero();
        assertThat(review.getResult()).isNull();
        assertThat(review.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("저장 결과는 API 계약의 필드 이름으로 직렬화되고 그대로 역직렬화된다")
    void reviewResult_roundTrip_keepsFixedFieldNames() throws Exception {
        // Given
        AiReviewResult result = new AiReviewResult(
                7.5,
                List.of(new Criterion(
                        "COMPOSITION",
                        "구도",
                        7.5
                )),
                true,
                "구도가 좋습니다."
        );

        // When
        String json = jsonMapper.writeValueAsString(result);
        Map<String, Object> tree = jsonMapper.readValue(json, new TypeReference<>() {
        });
        List<Map<String, Object>> criteria = jsonMapper.convertValue(
                tree.get("criteria"), new TypeReference<>() {
                });

        // Then
        assertThat(tree.keySet()).containsExactlyInAnyOrder(
                "overallScore",
                "criteria",
                "distinctivePerspective",
                "feedback"
        );
        assertThat(criteria.get(0).keySet()).containsExactlyInAnyOrder(
                "key",
                "label",
                "score"
        );
        assertThat(jsonMapper.readValue(json, AiReviewResult.class)).isEqualTo(result);
    }

    private static final Instant NOW = Instant.parse("2026-10-20T05:01:00Z");
    private static final Instant LEASE_END = Instant.parse("2026-10-20T05:03:00Z");
    private static final int MAX_CLAIMS = 3;
    private static final Invocation INVOCATION = new Invocation(
            1200,
            0,
            300,
            64,
            21_000L,
            "resp_abc"
    );

    @Test
    @DisplayName("대기 행을 가져가면 처리 중이 되고 시도 횟수·임대 만료·첫 시작 시각이 정해진다")
    void claim_queued_startsProcessing() {
        // Given
        AiReview review = queuedReview();

        // When
        boolean claimed = review.claim(
                NOW,
                LEASE_END,
                MAX_CLAIMS
        );

        // Then
        assertThat(claimed).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.PROCESSING);
        assertThat(review.getAttempt()).isEqualTo(1);
        assertThat(review.getLeaseExpiresAt()).isEqualTo(LEASE_END);
        assertThat(review.getStartedAt()).isEqualTo(NOW);
        assertThat(review.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("임대가 끝난 처리 중 행을 다시 가져가면 시도 횟수만 늘고 첫 시작 시각은 유지한다")
    void claim_expiredLease_reclaimsAndKeepsStartedAt() {
        // Given
        AiReview review = processingReview();
        Instant later = LEASE_END.plusSeconds(1);

        // When
        boolean claimed = review.claim(
                later,
                later.plusSeconds(120),
                MAX_CLAIMS
        );

        // Then
        assertThat(claimed).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.PROCESSING);
        assertThat(review.getAttempt()).isEqualTo(2);
        assertThat(review.getLeaseExpiresAt()).isEqualTo(later.plusSeconds(120));
        assertThat(review.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("임대가 남은 처리 중 행과 끝난 행은 가져갈 수 없다")
    void claim_notClaimable_throws() {
        // Given
        AiReview processing = processingReview();
        AiReview completed = processingReview();
        completed.complete(
                1,
                storedResult(),
                output(),
                INVOCATION,
                NOW
        );

        // When & Then
        assertThatThrownBy(() -> processing.claim(
                LEASE_END.minusSeconds(1),
                LEASE_END,
                MAX_CLAIMS
        ))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.claim(
                LEASE_END,
                LEASE_END.plusSeconds(120),
                MAX_CLAIMS
        ))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> completed.claim(
                NOW,
                LEASE_END,
                MAX_CLAIMS
        ))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("가져간 횟수가 상한을 넘으면 처리하지 않고 LEASE_EXHAUSTED로 실패시킨다")
    void claim_overMaxClaims_failsWithLeaseExhausted() {
        // Given
        AiReview review = queuedReview();
        Instant claimedAt = NOW;
        for (int count = 1; count <= MAX_CLAIMS; count++) {
            review.claim(
                    claimedAt,
                    claimedAt.plusSeconds(120),
                    MAX_CLAIMS
            );
            claimedAt = claimedAt.plusSeconds(121);
        }

        // When
        boolean claimed = review.claim(
                claimedAt,
                claimedAt.plusSeconds(120),
                MAX_CLAIMS
        );

        // Then
        assertThat(claimed).isFalse();
        assertThat(review.getStatus()).isEqualTo(Status.FAILED);
        assertThat(review.getErrorCode()).isEqualTo(FailureReason.LEASE_EXHAUSTED.name());
        assertThat(review.getAttempt()).isEqualTo(MAX_CLAIMS);
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getCompletedAt()).isEqualTo(claimedAt);
        assertThat(review.getResult()).isNull();
        assertThat(review.getOutput()).isNull();
    }

    @Test
    @DisplayName("현재 시도로 완료하면 결과·원출력·사용량을 저장하고 임대를 푼다")
    void complete_currentAttempt_storesResultAndReleasesLease() {
        // Given
        AiReview review = processingReview();
        Instant completedAt = NOW.plusSeconds(21);

        // When
        boolean applied = review.complete(
                1,
                storedResult(),
                output(),
                INVOCATION,
                completedAt
        );

        // Then
        assertThat(applied).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(review.getResult()).isEqualTo(storedResult());
        assertThat(review.getOutput()).isEqualTo(output());
        assertThat(review.getInputTokens()).isEqualTo(1200);
        assertThat(review.getCachedInputTokens()).isZero();
        assertThat(review.getOutputTokens()).isEqualTo(300);
        assertThat(review.getReasoningTokens()).isEqualTo(64);
        assertThat(review.getLatencyMs()).isEqualTo(21_000L);
        assertThat(review.getOpenaiResponseId()).isEqualTo("resp_abc");
        assertThat(review.getErrorCode()).isNull();
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("현재 시도로 실패하면 오류 코드와 받은 사용량을 저장하고 임대를 푼다")
    void fail_currentAttempt_storesErrorCodeAndInvocation() {
        // Given
        AiReview review = processingReview();

        // When
        boolean applied = review.fail(
                1,
                "INVALID_OUTPUT",
                INVOCATION,
                NOW
        );

        // Then
        assertThat(applied).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.FAILED);
        assertThat(review.getErrorCode()).isEqualTo("INVALID_OUTPUT");
        assertThat(review.getInputTokens()).isEqualTo(1200);
        assertThat(review.getLatencyMs()).isEqualTo(21_000L);
        assertThat(review.getOpenaiResponseId()).isEqualTo("resp_abc");
        assertThat(review.getResult()).isNull();
        assertThat(review.getOutput()).isNull();
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("응답을 받지 못한 실패는 사용량·지연·응답 ID를 비워 둔다")
    void fail_withoutInvocation_leavesUsageEmpty() {
        // Given
        AiReview review = processingReview();

        // When
        review.fail(
                1,
                FailureReason.POST_UNAVAILABLE.name(),
                Invocation.NONE,
                NOW
        );

        // Then
        assertThat(review.getStatus()).isEqualTo(Status.FAILED);
        assertThat(review.getInputTokens()).isNull();
        assertThat(review.getCachedInputTokens()).isNull();
        assertThat(review.getOutputTokens()).isNull();
        assertThat(review.getReasoningTokens()).isNull();
        assertThat(review.getLatencyMs()).isNull();
        assertThat(review.getOpenaiResponseId()).isNull();
    }

    @Test
    @DisplayName("시도 번호가 다른 늦은 워커의 완료·실패는 반영하지 않는다")
    void completeAndFail_staleAttempt_changesNothing() {
        // Given
        AiReview review = processingReview();
        review.claim(
                LEASE_END.plusSeconds(1),
                LEASE_END.plusSeconds(121),
                MAX_CLAIMS
        );

        // When
        boolean completed = review.complete(
                1,
                storedResult(),
                output(),
                INVOCATION,
                NOW
        );
        boolean failed = review.fail(
                1,
                "TIMEOUT",
                Invocation.NONE,
                NOW
        );

        // Then
        assertThat(completed).isFalse();
        assertThat(failed).isFalse();
        assertThat(review.getStatus()).isEqualTo(Status.PROCESSING);
        assertThat(review.getAttempt()).isEqualTo(2);
        assertThat(review.getResult()).isNull();
        assertThat(review.getErrorCode()).isNull();
        assertThat(review.getCompletedAt()).isNull();
        assertThat(review.getLeaseExpiresAt()).isEqualTo(LEASE_END.plusSeconds(121));
    }

    @Test
    @DisplayName("이미 끝난 행에는 완료·실패를 다시 반영하지 않는다")
    void completeAndFail_terminalReview_changesNothing() {
        // Given
        AiReview review = processingReview();
        review.complete(
                1,
                storedResult(),
                output(),
                INVOCATION,
                NOW
        );

        // When
        boolean completed = review.complete(
                1,
                storedResult(),
                output(),
                INVOCATION,
                NOW.plusSeconds(5)
        );
        boolean failed = review.fail(
                1,
                "TIMEOUT",
                Invocation.NONE,
                NOW.plusSeconds(5)
        );

        // Then
        assertThat(completed).isFalse();
        assertThat(failed).isFalse();
        assertThat(review.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(review.getCompletedAt()).isEqualTo(NOW);
        assertThat(review.getErrorCode()).isNull();
    }

    private AiReview queuedReview() {
        return AiReview.request(
                POST_ID,
                USER_ID,
                PROMPT_VERSION_ID,
                REQUEST_ID,
                REQUESTED_AT
        );
    }

    private AiReview processingReview() {
        AiReview review = queuedReview();
        review.claim(
                NOW,
                LEASE_END,
                MAX_CLAIMS
        );
        return review;
    }

    private AiReviewResult storedResult() {
        return new AiReviewResult(
                7.5,
                List.of(new Criterion(
                        "COMPOSITION",
                        "구도",
                        7.5
                )),
                true,
                "구도가 좋습니다."
        );
    }

    private String output() {
        return "{\"compositionScore\":7.46,\"themeExpressionScore\":8,\"feedback\":\"구도가 좋습니다.\"}";
    }
}
