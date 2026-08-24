package com.chalkak.backend.topic.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticipationPeriodTest {

    private static final Instant STARTS_AT = Instant.parse("2026-08-12T00:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    @DisplayName("참여 시작 전이면 공개 전 상태다")
    void phaseAt_beforeStartsAt_returnsBeforeOpen() {
        // Given
        ParticipationPeriod period = new ParticipationPeriod(STARTS_AT, ENDS_AT);
        Instant now = Instant.parse("2026-08-11T23:59:59Z");

        // When
        TopicPhase phase = period.phaseAt(now);

        // Then
        assertThat(phase).isEqualTo(TopicPhase.BEFORE_OPEN);
    }

    @Test
    @DisplayName("참여 기간 중이면 참여 가능 상태다")
    void phaseAt_withinPeriod_returnsOpen() {
        // Given
        ParticipationPeriod period = new ParticipationPeriod(STARTS_AT, ENDS_AT);
        Instant now = Instant.parse("2026-08-12T12:00:00Z");

        // When
        TopicPhase phase = period.phaseAt(now);

        // Then
        assertThat(phase).isEqualTo(TopicPhase.OPEN);
    }

    @Test
    @DisplayName("참여 기간이 지나면 참여 종료 상태다")
    void phaseAt_afterEndsAt_returnsClosed() {
        // Given
        ParticipationPeriod period = new ParticipationPeriod(STARTS_AT, ENDS_AT);
        Instant now = Instant.parse("2026-08-13T00:00:01Z");

        // When
        TopicPhase phase = period.phaseAt(now);

        // Then
        assertThat(phase).isEqualTo(TopicPhase.CLOSED);
    }

    @Test
    @DisplayName("참여 시작 시각이면 참여 가능 상태다")
    void phaseAt_exactlyStartsAt_returnsOpen() {
        // Given
        ParticipationPeriod period = new ParticipationPeriod(STARTS_AT, ENDS_AT);

        // When
        TopicPhase phase = period.phaseAt(STARTS_AT);

        // Then
        assertThat(phase).isEqualTo(TopicPhase.OPEN);
    }

    @Test
    @DisplayName("참여 종료 시각이면 참여 종료 상태다")
    void phaseAt_exactlyEndsAt_returnsClosed() {
        // Given
        ParticipationPeriod period = new ParticipationPeriod(STARTS_AT, ENDS_AT);

        // When
        TopicPhase phase = period.phaseAt(ENDS_AT);

        // Then
        assertThat(phase).isEqualTo(TopicPhase.CLOSED);
    }
}
