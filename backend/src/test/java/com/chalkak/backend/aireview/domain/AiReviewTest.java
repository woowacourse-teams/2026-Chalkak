package com.chalkak.backend.aireview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.aireview.domain.AiReview.FailureReason;
import com.chalkak.backend.aireview.domain.AiReview.Invocation;
import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.domain.AiReviewResult.Criterion;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class AiReviewTest {

    private static final UUID ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-20T05:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-20T05:01:00Z");
    private static final Instant LEASE_END = Instant.parse("2026-10-20T05:03:00Z");
    private static final Instant RETRY_AT = Instant.parse("2026-10-20T05:01:40Z");
    private static final Instant AGAIN_AT = Instant.parse("2026-10-20T06:00:00Z");
    private static final UUID NEW_VERSION_ID = UUID
            .fromString("019a0000-0000-7000-8000-000000000002");
    private static final int MAX_CLAIMS = 3;
    private static final int MAX_REQUESTS = 3;
    private static final Invocation INVOCATION = new Invocation(
            1200,
            0,
            300,
            64,
            21_000L,
            "resp_abc"
    );
    private static final AiReviewResult RESULT = new AiReviewResult(
            7.5,
            List.of(new Criterion(
                    "COMPOSITION",
                    "구도",
                    7.5
            )),
            true,
            new AiReviewResult.Feedback(
                    "구도가 좋습니다.",
                    "빛이 좋습니다.",
                    "수평이 아쉽습니다.",
                    "낮게 찍어 보세요."
            )
    );
    private static final String OUTPUT = "{\"compositionScore\":7.46,\"feedback\":\"구도가 좋습니다.\"}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("요청하면 대기 상태로 시작하고 시도 횟수·결과·완료 시각은 비어 있다")
    void request_newReview_startsQueued() {
        // When
        AiReview review = queuedReview();

        // Then
        assertThat(review.getStatus()).isEqualTo(Status.QUEUED);
        assertThat(review.getAttempt()).isZero();
        assertThat(review.getRoundAttempt()).isZero();
        assertThat(review.getRequestCount()).isEqualTo(1);
        assertThat(review.getLastRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(review.getNextAttemptAt()).isNull();
        assertThat(review.getResult()).isNull();
        assertThat(review.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("저장 결과는 API 계약의 필드 이름으로 직렬화되고 그대로 역직렬화된다")
    void reviewResult_roundTrip_keepsFixedFieldNames() throws Exception {
        // When
        String json = jsonMapper.writeValueAsString(RESULT);
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
        assertThat(jsonMapper.readValue(json, AiReviewResult.class)).isEqualTo(RESULT);
    }

    @Test
    @DisplayName("대기 행을 가져가면 처리 중이 되고 시도 횟수·임대 만료·첫 시작 시각이 정해진다")
    void claim_queued_startsProcessing() {
        // Given
        AiReview review = queuedReview();

        // When
        boolean claimed = claim(review, NOW);

        // Then
        assertThat(claimed).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.PROCESSING);
        assertThat(review.getAttempt()).isEqualTo(1);
        assertThat(review.getRoundAttempt()).isEqualTo(1);
        assertThat(review.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(review.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("임대가 끝난 처리 중 행을 다시 가져가면 시도 번호와 회차 시도 횟수가 늘고 첫 시작 시각은 유지한다")
    void claim_expiredLease_reclaimsAndKeepsStartedAt() {
        // Given
        AiReview review = processingReview();
        Instant later = LEASE_END.plusSeconds(1);

        // When
        boolean claimed = claim(review, later);

        // Then
        assertThat(claimed).isTrue();
        assertThat(review.getAttempt()).isEqualTo(2);
        assertThat(review.getRoundAttempt()).isEqualTo(2);
        assertThat(review.getLeaseExpiresAt()).isEqualTo(later.plusSeconds(120));
        assertThat(review.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("임대가 남은 처리 중 행과 끝난 행은 가져갈 수 없다")
    void claim_notClaimable_throws() {
        // Given
        AiReview processing = processingReview();
        AiReview completed = processingReview();
        complete(completed, 1);

        // When & Then
        assertThatThrownBy(() -> claim(processing, LEASE_END.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> claim(processing, LEASE_END))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> claim(completed, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("회차 안에서 가져간 횟수가 상한을 넘으면 처리하지 않고 시도 번호를 늘리지 않은 채 LEASE_EXHAUSTED로 실패시킨다")
    void claim_overMaxClaims_failsWithLeaseExhausted() {
        // Given
        AiReview review = queuedReview();
        Instant claimedAt = NOW;
        for (int count = 1; count <= MAX_CLAIMS; count++) {
            claim(review, claimedAt);
            claimedAt = claimedAt.plusSeconds(121);
        }

        // When
        boolean claimed = claim(review, claimedAt);

        // Then
        assertThat(claimed).isFalse();
        assertThat(review.getStatus()).isEqualTo(Status.FAILED);
        assertThat(review.getErrorCode()).isEqualTo(FailureReason.LEASE_EXHAUSTED.name());
        assertThat(review.getAttempt()).isEqualTo(MAX_CLAIMS);
        assertThat(review.getRoundAttempt()).isEqualTo(MAX_CLAIMS);
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getCompletedAt()).isEqualTo(claimedAt);
    }

    @Test
    @DisplayName("재시도 뒤 임대 회수로 회차를 소진하면 이전 시도의 사용량과 응답 ID를 비우고 LEASE_EXHAUSTED로 끝낸다")
    void claim_exhaustedAfterRetry_clearsPreviousInvocation() {
        // Given
        AiReview review = retryWaitingReview();
        claim(review, RETRY_AT);
        claim(review, RETRY_AT.plusSeconds(121));
        assertThat(review.getInvocation()).isEqualTo(INVOCATION);

        // When
        boolean claimed = claim(review, RETRY_AT.plusSeconds(242));

        // Then
        assertThat(claimed).isFalse();
        assertThat(review.getStatus()).isEqualTo(Status.FAILED);
        assertThat(review.getErrorCode()).isEqualTo(FailureReason.LEASE_EXHAUSTED.name());
        assertThat(review.getInvocation()).isEqualTo(Invocation.NONE);
    }

    @Test
    @DisplayName("재시도 대기 행은 다음 시도 시각 전에는 가져갈 수 없고, 그 시각부터 가져가면 시도 횟수가 늘고 다음 시도 시각이 비워진다")
    void claim_retryWaiting_claimableOnlyFromNextAttemptAt() {
        // Given
        AiReview review = retryWaitingReview();

        // When & Then
        assertThatThrownBy(() -> claim(review, RETRY_AT.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(review.getStatus()).isEqualTo(Status.QUEUED);

        assertThat(claim(review, RETRY_AT)).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.PROCESSING);
        assertThat(review.getAttempt()).isEqualTo(2);
        assertThat(review.getRoundAttempt()).isEqualTo(2);
        assertThat(review.getNextAttemptAt()).isNull();
        assertThat(review.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("현재 시도로 완료하면 결과·원출력·사용량을 저장하고 임대를 푼다")
    void complete_currentAttempt_storesResultAndReleasesLease() {
        // Given
        AiReview review = processingReview();

        // When
        boolean applied = complete(review, 1);

        // Then
        assertThat(applied).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(review.getResult()).isEqualTo(RESULT);
        assertThat(review.getOutput()).isEqualTo(OUTPUT);
        assertThat(review.getInvocation().inputTokens()).isEqualTo(1200);
        assertThat(review.getInvocation().cachedInputTokens()).isZero();
        assertThat(review.getInvocation().outputTokens()).isEqualTo(300);
        assertThat(review.getInvocation().reasoningTokens()).isEqualTo(64);
        assertThat(review.getInvocation().latencyMs()).isEqualTo(21_000L);
        assertThat(review.getInvocation().openaiResponseId()).isEqualTo("resp_abc");
        assertThat(review.getErrorCode()).isNull();
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("재시도할 수 있고 회차 예산이 남았으면 대기로 되돌리고 다음 시도 시각·마지막 시도의 사유와 사용량을 남기며 임대를 푼다")
    void failOrRetry_retryableWithBudget_requeuesWithNextAttemptAt() {
        // Given
        AiReview review = processingReview();

        // When
        boolean applied = failOrRetry(
                review,
                1,
                "TIMEOUT",
                true
        );

        // Then
        assertThat(applied).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.QUEUED);
        assertThat(review.getNextAttemptAt()).isEqualTo(RETRY_AT);
        assertThat(review.getErrorCode()).isEqualTo("TIMEOUT");
        assertThat(review.getInvocation().inputTokens()).isEqualTo(1200);
        assertThat(review.getInvocation().openaiResponseId()).isEqualTo("resp_abc");
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getCompletedAt()).isNull();
        assertThat(review.getAttempt()).isEqualTo(1);
        assertThat(review.getRoundAttempt()).isEqualTo(1);
        assertThat(review.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("마지막 시도까지 재시도 가능한 사유로 실패하면 그 사유로 실패를 확정한다")
    void failOrRetry_retryableWithoutBudget_failsWithLastReason() {
        // Given
        AiReview review = retryWaitingReview();
        claim(review, RETRY_AT);
        claim(review, RETRY_AT.plusSeconds(121));

        // When
        boolean applied = review.failOrRetry(
                3,
                "RATE_LIMITED",
                true,
                INVOCATION,
                RETRY_AT,
                MAX_CLAIMS,
                NOW
        );

        // Then
        assertThat(applied).isTrue();
        assertThat(review.getRoundAttempt()).isEqualTo(MAX_CLAIMS);
        assertThat(review.getStatus()).isEqualTo(Status.FAILED);
        assertThat(review.getErrorCode()).isEqualTo("RATE_LIMITED");
        assertThat(review.getNextAttemptAt()).isNull();
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("재시도할 수 없는 사유는 예산이 남아 있어도 바로 실패하고 받은 사용량을 저장한다")
    void failOrRetry_notRetryable_failsImmediately() {
        // Given
        AiReview review = processingReview();

        // When
        boolean applied = failOrRetry(
                review,
                1,
                "INVALID_OUTPUT",
                false
        );

        // Then
        assertThat(applied).isTrue();
        assertThat(review.getStatus()).isEqualTo(Status.FAILED);
        assertThat(review.getErrorCode()).isEqualTo("INVALID_OUTPUT");
        assertThat(review.getInvocation().inputTokens()).isEqualTo(1200);
        assertThat(review.getInvocation().latencyMs()).isEqualTo(21_000L);
        assertThat(review.getInvocation().openaiResponseId()).isEqualTo("resp_abc");
        assertThat(review.getResult()).isNull();
        assertThat(review.getOutput()).isNull();
        assertThat(review.getNextAttemptAt()).isNull();
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("응답을 받지 못한 실패는 사용량·지연·응답 ID를 비워 둔다")
    void failOrRetry_withoutInvocation_leavesUsageEmpty() {
        // Given
        AiReview review = processingReview();

        // When
        review.failOrRetry(
                1,
                FailureReason.POST_UNAVAILABLE.name(),
                false,
                Invocation.NONE,
                RETRY_AT,
                MAX_CLAIMS,
                NOW
        );

        // Then
        assertThat(review.getInvocation().inputTokens()).isNull();
        assertThat(review.getInvocation().cachedInputTokens()).isNull();
        assertThat(review.getInvocation().outputTokens()).isNull();
        assertThat(review.getInvocation().reasoningTokens()).isNull();
        assertThat(review.getInvocation().latencyMs()).isNull();
        assertThat(review.getInvocation().openaiResponseId()).isNull();
    }

    @Test
    @DisplayName("시도 번호가 다른 늦은 워커나 이미 끝난 행에는 완료·실패·재시도를 반영하지 않는다")
    void completeAndFailOrRetry_staleOrTerminal_changesNothing() {
        // Given
        AiReview reclaimed = processingReview();
        claim(reclaimed, LEASE_END.plusSeconds(1));
        AiReview finished = processingReview();
        complete(finished, 1);

        // When & Then
        assertThat(complete(reclaimed, 1)).isFalse();
        assertThat(failOrRetry(
                reclaimed,
                1,
                "TIMEOUT",
                true
        )).isFalse();
        assertThat(reclaimed.getStatus()).isEqualTo(Status.PROCESSING);
        assertThat(reclaimed.getAttempt()).isEqualTo(2);
        assertThat(reclaimed.getErrorCode()).isNull();
        assertThat(reclaimed.getNextAttemptAt()).isNull();
        assertThat(reclaimed.getCompletedAt()).isNull();

        assertThat(complete(finished, 1)).isFalse();
        assertThat(failOrRetry(
                finished,
                1,
                "TIMEOUT",
                true
        )).isFalse();
        assertThat(finished.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(finished.getErrorCode()).isNull();
    }

    @Test
    @DisplayName("실패한 리뷰를 다시 요청하면 대기로 되돌려 요청 횟수를 늘리고 회차 상태와 사용량을 비우며 버전을 다시 고정한다")
    void requestAgain_failed_requeuesWithFreshRound() {
        // Given
        AiReview review = failedReview();

        // When
        review.requestAgain(NEW_VERSION_ID, AGAIN_AT);

        // Then
        assertThat(review.getStatus()).isEqualTo(Status.QUEUED);
        assertThat(review.getRequestCount()).isEqualTo(2);
        assertThat(review.getLastRequestedAt()).isEqualTo(AGAIN_AT);
        assertThat(review.getRoundAttempt()).isZero();
        assertThat(review.getPromptVersionId()).isEqualTo(NEW_VERSION_ID);
        assertThat(review.getStartedAt()).isNull();
        assertThat(review.getCompletedAt()).isNull();
        assertThat(review.getErrorCode()).isNull();
        assertThat(review.getNextAttemptAt()).isNull();
        assertThat(review.getLeaseExpiresAt()).isNull();
        assertThat(review.getInvocation()).isEqualTo(Invocation.NONE);
    }

    @Test
    @DisplayName("다시 요청해도 시도 번호와 최초 요청 ID·시각은 그대로다")
    void requestAgain_failed_keepsAttemptAndFirstRequest() {
        // Given
        AiReview review = failedReview();

        // When
        review.requestAgain(NEW_VERSION_ID, AGAIN_AT);

        // Then
        assertThat(review.getAttempt()).isEqualTo(1);
        assertThat(review.getRequestId()).isEqualTo(ID);
        assertThat(review.getRequestedAt()).isEqualTo(REQUESTED_AT);
    }

    @Test
    @DisplayName("실패가 아닌 리뷰는 다시 요청할 수 없다")
    void requestAgain_notFailed_throws() {
        // Given
        AiReview queued = queuedReview();
        AiReview processing = processingReview();
        AiReview completed = processingReview();
        complete(completed, 1);

        // When & Then
        List<AiReview> reviews = List.of(
                queued,
                processing,
                completed
        );
        for (AiReview review : reviews) {
            Status before = review.getStatus();
            assertThatThrownBy(() -> review.requestAgain(NEW_VERSION_ID, AGAIN_AT))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(review.getStatus()).isEqualTo(before);
            assertThat(review.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("요청 횟수가 상한에 닿은 리뷰의 다시 요청은 400 업무 예외로 거절하고 아무것도 바꾸지 않는다")
    void requestAgain_limitReached_throwsBusinessException() {
        // Given
        AiReview review = failedAfterRequests(MAX_REQUESTS);

        // When & Then
        assertThatThrownBy(() -> review.requestAgain(NEW_VERSION_ID, AGAIN_AT))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception).hasMessage("더 이상 AI 리뷰를 다시 요청할 수 없습니다.");
                });
        assertThat(review.getStatus()).isEqualTo(Status.FAILED);
        assertThat(review.getRequestCount()).isEqualTo(MAX_REQUESTS);
        assertThat(review.getPromptVersionId()).isEqualTo(ID);
        assertThat(review.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("요청 횟수가 남았으면 횟수 확인을 통과하고 상한에 닿으면 400 업무 예외다")
    void validateRequestsLeft_byRequestCount() {
        // When & Then
        failedAfterRequests(MAX_REQUESTS - 1).validateRequestsLeft();
        assertThatThrownBy(() -> failedAfterRequests(MAX_REQUESTS).validateRequestsLeft())
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception).hasMessage("더 이상 AI 리뷰를 다시 요청할 수 없습니다.");
                });
    }

    @Test
    @DisplayName("실패한 리뷰만 실패 상태로 묻는다")
    void isFailed_byStatus() {
        // When & Then
        assertThat(failedReview().isFailed()).isTrue();
        assertThat(queuedReview().isFailed()).isFalse();
        assertThat(processingReview().isFailed()).isFalse();
    }

    @Test
    @DisplayName("결과 알림 대상은 완료와 게시물 사용 불가를 제외한 실패뿐이다")
    void isResultNotifiable_byStateAndErrorCode() {
        // Given
        AiReview postUnavailable = processingReview();
        failOrRetry(
                postUnavailable,
                1,
                FailureReason.POST_UNAVAILABLE.name(),
                false
        );
        AiReview leaseExhausted = processingReview();
        leaseExhausted.claim(
                LEASE_END.plusSeconds(1),
                LEASE_END.plusSeconds(120),
                1
        );

        // When & Then
        assertThat(reviewInStatus(Status.COMPLETED, 1).isResultNotifiable()).isTrue();
        assertThat(failedReview().isResultNotifiable()).isTrue();
        assertThat(leaseExhausted.getErrorCode())
                .isEqualTo(FailureReason.LEASE_EXHAUSTED.name());
        assertThat(leaseExhausted.isResultNotifiable()).isTrue();
        assertThat(postUnavailable.isResultNotifiable()).isFalse();
        assertThat(retryWaitingReview().isResultNotifiable()).isFalse();
        assertThat(queuedReview().isResultNotifiable()).isFalse();
        assertThat(processingReview().isResultNotifiable()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "QUEUED, 1, false",
            "PROCESSING, 1, false",
            "COMPLETED, 1, false",
            "FAILED, 1, true",
            "FAILED, 2, true",
            "FAILED, 3, false"
    })
    @DisplayName("다시 요청할 수 있는지는 실패이고 요청 횟수가 상한 미만일 때만 참이다")
    void canRequestAgain_byStatusAndRequestCount(
            Status status,
            int requestCount,
            boolean expected
    ) {
        // Given
        AiReview review = reviewInStatus(status, requestCount);

        // When & Then
        assertThat(review.getRequestCount()).isEqualTo(requestCount);
        assertThat(review.canRequestAgain()).isEqualTo(expected);
    }

    @Test
    @DisplayName("다시 요청한 뒤에는 이전 회차에서 시도하던 워커의 완료·실패·재시도를 반영하지 않는다")
    void completeAndFailOrRetry_afterRequestAgain_ignoresPreviousRoundAttempt() {
        // Given
        AiReview review = failedReview();
        review.requestAgain(NEW_VERSION_ID, AGAIN_AT);

        // When & Then
        assertThat(complete(review, 1)).isFalse();
        assertThat(failOrRetry(
                review,
                1,
                "TIMEOUT",
                true
        )).isFalse();
        assertThat(review.getStatus()).isEqualTo(Status.QUEUED);

        claim(review, AGAIN_AT);
        assertThat(review.getAttempt()).isEqualTo(2);
        assertThat(complete(review, 1)).isFalse();
        assertThat(failOrRetry(
                review,
                1,
                "TIMEOUT",
                true
        )).isFalse();
        assertThat(review.getStatus()).isEqualTo(Status.PROCESSING);
        assertThat(review.getResult()).isNull();
    }

    private AiReview failedReview() {
        AiReview review = processingReview();
        failOrRetry(
                review,
                1,
                "INVALID_OUTPUT",
                false
        );
        return review;
    }

    private AiReview failedAfterRequests(int requests) {
        AiReview review = failedReview();
        for (int count = 2; count <= requests; count++) {
            review.requestAgain(ID, AGAIN_AT);
            int attempt = review.getAttempt() + 1;
            claim(review, AGAIN_AT);
            failOrRetry(
                    review,
                    attempt,
                    "INVALID_OUTPUT",
                    false
            );
        }
        return review;
    }

    private AiReview reviewInStatus(Status status, int requestCount) {
        if (status == Status.QUEUED) {
            return queuedReview();
        }
        if (status == Status.PROCESSING) {
            return processingReview();
        }
        if (status == Status.COMPLETED) {
            AiReview review = processingReview();
            complete(review, 1);
            return review;
        }
        return failedAfterRequests(requestCount);
    }

    private AiReview queuedReview() {
        return AiReview.request(
                ID,
                ID,
                ID,
                ID,
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

    private boolean claim(AiReview review, Instant at) {
        return review.claim(
                at,
                at.plusSeconds(120),
                MAX_CLAIMS
        );
    }

    private boolean complete(AiReview review, int attempt) {
        return review.complete(
                attempt,
                RESULT,
                OUTPUT,
                INVOCATION,
                NOW
        );
    }

    private AiReview retryWaitingReview() {
        AiReview review = processingReview();
        failOrRetry(
                review,
                1,
                "TIMEOUT",
                true
        );
        return review;
    }

    private boolean failOrRetry(
            AiReview review,
            int attempt,
            String errorCode,
            boolean retryable
    ) {
        return review.failOrRetry(
                attempt,
                errorCode,
                retryable,
                INVOCATION,
                RETRY_AT,
                MAX_CLAIMS,
                NOW
        );
    }
}
