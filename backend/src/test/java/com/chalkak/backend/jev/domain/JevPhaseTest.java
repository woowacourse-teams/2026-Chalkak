package com.chalkak.backend.jev.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JevPhaseTest {

    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    @DisplayName("단계 예산은 첫 확보부터 100초이며 물리 호출 재확보로 초기화하지 않는다")
    void claim_retry_keepsDeadlineAndCapsThreeCalls() {
        // Given
        JevPhase phase = JevPhase.ready(NOW).claim(NOW);
        Instant deadline = phase.deadline();
        // When
        for (int attempt = 0; attempt < 3; attempt++) {
            Instant end = phase.claimedAt().plusSeconds(1);
            phase = phase.receive(
                    result(phase, "TIMEOUT"),
                    end,
                    0
            );
            assertThat(phase.deadline()).isEqualTo(deadline);
            if (attempt < 2) {
                phase = phase.claim(phase.availableAt());
            }
        }
        // Then
        assertThat(deadline).isEqualTo(NOW.plusSeconds(100));
        assertThat(phase.completed()).isTrue();
        assertThat(phase.calls()).hasSize(3);
        assertThat(phase.failure()).isEqualTo("TIMEOUT");
        JevPhase finished = phase;
        assertThatThrownBy(() -> finished.claim(NOW.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Retry-After가 단계 기한을 넘으면 추가 호출 없이 종료한다")
    void receive_retryAfterExceedsBudget_finishes() {
        // Given
        JevPhase phase = JevPhase.ready(NOW).claim(NOW);
        JevCallResult result = new JevCallResult(
                phase.callId(),
                NOW,
                NOW.plusSeconds(1),
                null,
                "HTTP_ERROR",
                429,
                1000L,
                120
        );
        // When
        phase = phase.receive(
                result,
                NOW.plusSeconds(1),
                0
        );
        // Then
        assertThat(phase.completed()).isTrue();
        assertThat(phase.calls()).hasSize(1);
    }

    @Test
    @DisplayName("살아 있는 호출은 중복 확보할 수 없고 유실되면 기록 후 대기한다")
    void refresh_lostResponse_retainsAttemptBeforeRetry() {
        // Given
        JevPhase phase = JevPhase.ready(NOW).claim(NOW);
        // When
        assertThat(phase.canClaim(NOW.plusSeconds(44))).isFalse();
        phase = phase.refresh(NOW.plusSeconds(45));
        // Then
        assertThat(phase.calls()).hasSize(1);
        assertThat(phase.calls().getFirst().errorCode()).isEqualTo("RESPONSE_LOST");
        assertThat(phase.canClaim(NOW.plusSeconds(46))).isFalse();
        assertThat(phase.canClaim(NOW.plusSeconds(47))).isTrue();
    }

    @Test
    @DisplayName("늦은 응답은 사용량 복구용 원문만 남기고 새 호출을 완료하지 않는다")
    void receive_lateResponse_doesNotCompleteNewCall() {
        // Given
        JevPhase first = JevPhase.ready(NOW).claim(NOW);
        JevPhase phase = first.refresh(NOW.plusSeconds(45)).claim(NOW.plusSeconds(47));
        UUID secondCall = phase.callId();
        // When
        phase = phase.receive(
                result(first, null),
                NOW.plusSeconds(48),
                0
        );
        // Then
        assertThat(phase.callId()).isEqualTo(secondCall);
        assertThat(phase.completed()).isFalse();
        assertThat(phase.calls().getFirst().errorCode()).isEqualTo("LATE_RESULT_IGNORED");
        assertThat(phase.calls().getFirst().rawResponse()).isEqualTo("{}");
    }

    @Test
    @DisplayName("기한에 도착한 성공 응답도 자동 성공으로 바꾸지 않는다")
    void receive_atDeadline_finishesAsError() {
        // Given
        JevPhase phase = JevPhase.ready(NOW).claim(NOW);
        // When
        phase = phase.receive(
                result(phase, null),
                NOW.plusSeconds(100),
                0
        );
        // Then
        assertThat(phase.failure()).isEqualTo("DEADLINE_EXCEEDED");
    }

    @Test
    @DisplayName("영구 오류는 재시도하지 않으며 결과 재전달로 호출 기록을 중복하지 않는다")
    void receive_permanentErrorAndDuplicate_keepsOneCall() {
        // Given
        JevPhase phase = JevPhase.ready(NOW).claim(NOW);
        JevCallResult result = result(phase, "CONFIGURATION_ERROR");
        // When
        phase = phase.receive(
                result,
                NOW.plusSeconds(1),
                0
        );
        phase = phase.receive(
                result,
                NOW.plusSeconds(2),
                0
        );
        // Then
        assertThat(phase.completed()).isTrue();
        assertThat(phase.calls()).hasSize(1);
    }

    private JevCallResult result(JevPhase phase, String error) {
        return new JevCallResult(
                phase.callId(),
                NOW,
                NOW.plusSeconds(1),
                "{}",
                error,
                error == null ? 200 : null,
                1000L,
                null
        );
    }
}
