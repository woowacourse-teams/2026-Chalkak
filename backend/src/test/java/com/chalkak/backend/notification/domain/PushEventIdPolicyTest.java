package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PushEventIdPolicyTest {

    private static final UUID SCHEDULE_ID = new UUID(0, 1);
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-26T00:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "00000000-0000-0000-0000-000000000001, 1, 807e0c95-3d92-32cf-856b-0c218d5b5fc0",
            "00000000-0000-0000-0000-000000000001, 2, ad376108-5525-3dc2-89a4-ddf3a2f1c816",
            "00000000-0000-0000-0000-000000000002, 1, 7031fcc2-2fc9-39bc-ade3-d9e457ef86a3"
    })
    @DisplayName("리뷰 ID와 회차별 AI 리뷰 사건 ID는 기존에 발행한 UUID 형식을 유지한다")
    void createForAiReview_knownReviewAndRound_preservesPublishedFormat(
            UUID aiReviewId,
            int round,
            UUID expected
    ) {
        // When
        UUID first = PushEventIdPolicy.createForAiReview(aiReviewId, round);
        UUID repeated = PushEventIdPolicy.createForAiReview(aiReviewId, round);

        // Then
        assertThat(first).isEqualTo(expected);
        assertThat(repeated).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"TOPIC", "REMINDER"})
    @DisplayName("같은 종류·설정·예정 시각은 다시 생성해도 같은 사건 ID를 사용한다")
    void createForOccurrence_sameOccurrence_preservesEventId(NotificationType type) {
        // When
        UUID first = PushEventIdPolicy.createForOccurrence(
                type,
                SCHEDULE_ID,
                OCCURRED_AT
        );
        UUID repeated = PushEventIdPolicy.createForOccurrence(
                type,
                SCHEDULE_ID,
                OCCURRED_AT
        );

        // Then
        assertThat(repeated).isEqualTo(first);
    }

    @Test
    @DisplayName("배포 후에도 같은 회차를 식별하도록 사건 ID 생성 형식을 유지한다")
    void createForOccurrence_knownOccurrence_preservesPublishedFormat() {
        // When
        UUID eventId = PushEventIdPolicy.createForOccurrence(
                NotificationType.TOPIC,
                SCHEDULE_ID,
                OCCURRED_AT
        );

        // Then
        assertThat(eventId).isEqualTo(UUID.fromString("aa1144dc-4e9a-37cc-9484-bb1f0e809bd5"));
    }

    @Test
    @DisplayName("같은 시각과 ID여도 주제와 리마인더는 다른 사건으로 식별한다")
    void createForOccurrence_differentType_usesDifferentEventId() {
        // When
        UUID topic = PushEventIdPolicy.createForOccurrence(
                NotificationType.TOPIC,
                SCHEDULE_ID,
                OCCURRED_AT
        );
        UUID reminder = PushEventIdPolicy.createForOccurrence(
                NotificationType.REMINDER,
                SCHEDULE_ID,
                OCCURRED_AT
        );

        // Then
        assertThat(topic).isNotEqualTo(reminder);
    }

    @Test
    @DisplayName("예정 시각이 같아도 설정 ID가 다르면 다른 사건으로 식별한다")
    void createForOccurrence_differentSchedule_usesDifferentEventId() {
        // When
        UUID first = PushEventIdPolicy.createForOccurrence(
                NotificationType.TOPIC,
                SCHEDULE_ID,
                OCCURRED_AT
        );
        UUID other = PushEventIdPolicy.createForOccurrence(
                NotificationType.TOPIC,
                new UUID(0, 2),
                OCCURRED_AT
        );

        // Then
        assertThat(first).isNotEqualTo(other);
    }

    @ParameterizedTest
    @ValueSource(longs = {60, 86400})
    @DisplayName("같은 설정이어도 같은 날의 다른 시각과 다음 날 회차는 다른 사건으로 식별한다")
    void createForOccurrence_differentTime_usesDifferentEventId(long seconds) {
        // When
        UUID first = PushEventIdPolicy.createForOccurrence(
                NotificationType.REMINDER,
                SCHEDULE_ID,
                OCCURRED_AT
        );
        UUID other = PushEventIdPolicy.createForOccurrence(
                NotificationType.REMINDER,
                SCHEDULE_ID,
                OCCURRED_AT.plusSeconds(seconds)
        );

        // Then
        assertThat(first).isNotEqualTo(other);
    }
}
