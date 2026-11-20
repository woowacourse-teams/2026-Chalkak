package com.chalkak.backend.jev.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JevModerationRunTest {

    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    @DisplayName("작업 전달은 4회까지 허용하고 다음 예약부터 기술 검토로 종료한다")
    void reserveDispatch_deliveryLimit_finishesWithReview() {
        // Given
        JevModerationRun run = create();
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(run.reserveDispatch(NOW.plusSeconds((attempt - 1) * 15L)))
                    .isEqualTo(attempt);
        }
        assertThat(run.getFinishedAt()).isNull();
        // When
        assertThat(run.reserveDispatch(NOW.plusSeconds(45))).isEqualTo(4);
        assertThat(run.getFinishedAt()).isNull();
        // Then
        assertThat(run.reserveDispatch(NOW.plusSeconds(60))).isZero();
        assertThat(run.getDispatchAttempts()).isEqualTo(4);
        assertThat(run.getUnclaimedDispatchAttempts()).isEqualTo(4);
        assertThat(run.getEvaluation().error()).isEqualTo("DISPATCH_LIMIT_EXCEEDED");
        assertThat(run.getEvaluation().recommendation()).isEqualTo(JevDecision.HUMAN_REVIEW);
        assertThat(run.getFinishedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(run.reserveDispatch(NOW.plusSeconds(75))).isZero();
        assertThat(run.getFinishedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("전달 한도에 도달해도 이미 시작한 사진 검사의 100초 기한은 보장한다")
    void reserveDispatch_photoInFlight_waitsUntilPhaseDeadline() {
        // Given
        JevModerationRun run = create();
        run.updatePhase(run.getPhase().claim(NOW));
        for (int attempt = 1; attempt <= 4; attempt++) {
            run.reserveDispatch(NOW.plusSeconds(attempt * 15L));
        }
        // When & Then
        assertThat(run.reserveDispatch(NOW.plusSeconds(75))).isZero();
        assertThat(run.getFinishedAt()).isNull();
        assertThat(run.getDispatchAt()).isEqualTo(NOW.plusSeconds(100));
        assertThat(run.reserveDispatch(NOW.plusSeconds(100).minusNanos(1))).isZero();
        assertThat(run.getFinishedAt()).isNull();
        assertThat(run.reserveDispatch(NOW.plusSeconds(100))).isZero();
        assertThat(run.getEvaluation().error()).isEqualTo("DISPATCH_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("전달 실패로 종료한 기술 검토는 늦게 온 승인 평가로 덮어쓰지 않는다")
    void complete_afterDispatchFailure_preservesTechnicalReview() {
        // Given
        JevModerationRun run = create();
        for (int attempt = 0; attempt <= 4; attempt++) {
            run.reserveDispatch(NOW.plusSeconds(attempt * 15L));
        }
        JevEvaluation late = new JevEvaluation(
                java.util.Map.of(),
                java.util.Map.of(),
                JevDecision.APPROVE,
                null,
                10L,
                3L,
                null
        );
        // When
        run.complete(late, NOW.plusSeconds(61));
        // Then
        assertThat(run.getEvaluation().error()).isEqualTo("DISPATCH_LIMIT_EXCEEDED");
        assertThat(run.getEvaluation().recommendation()).isEqualTo(JevDecision.HUMAN_REVIEW);
        assertThat(run.getFinishedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("작업 확보는 미확보 전달 횟수만 초기화하고 누적 횟수와 모델 예산은 유지한다")
    void acknowledgeDispatch_claimed_resetsOnlyUnclaimedDeliveries() {
        // Given
        JevModerationRun run = create();
        for (int attempt = 0; attempt < 4; attempt++) {
            run.reserveDispatch(NOW.plusSeconds(attempt * 15L));
        }
        run.updatePhase(run.getPhase().claim(NOW.plusSeconds(45)));
        JevPhase phase = run.getPhase();
        // When
        run.acknowledgeDispatch();
        // Then
        assertThat(run.getUnclaimedDispatchAttempts()).isZero();
        assertThat(run.getDispatchAttempts()).isEqualTo(4);
        assertThat(run.getPhase()).isEqualTo(phase);
        assertThat(run.reserveDispatch(NOW.plusSeconds(60))).isEqualTo(5);
        assertThat(run.getUnclaimedDispatchAttempts()).isEqualTo(1);
        assertThat(run.getFinishedAt()).isNull();
    }

    private JevModerationRun create() {
        return JevModerationRun.preview(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "초록",
                "chalkak/posts/test/original/test.webp",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new JevPromptDraft(
                        "jev-1.14.0",
                        JevPromptFixtures.classification(),
                        JevPromptFixtures.photo()
                ),
                NOW
        );
    }
}
