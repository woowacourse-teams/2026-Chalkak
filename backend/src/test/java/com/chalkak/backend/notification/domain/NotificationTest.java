package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();
    private static final UUID EVENT_KEY = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-06T00:00:00Z");

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("푸시 기한은 사건 발생 후 30분부터 만료다")
    void isPushExpired_deadlineBoundary_expiresAtThirtyMinutes(long seconds) {
        // Given
        Notification notification = Notification.approved(USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT,
                true);

        // When & Then
        assertThat(notification.isPushExpired(OCCURRED_AT.plusSeconds(seconds)))
                .isEqualTo(seconds >= 1800);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1739, 1740, 1799, 1800, 1801})
    @DisplayName("발행 재시도는 1분 뒤이며 기한을 넘기지 않는다")
    void retrySqsPublication_attemptTime_limitsRetryToDeadline(long seconds) {
        // Given
        Notification notification = Notification.approved(USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT,
                true);
        Instant attemptedAt = OCCURRED_AT.plusSeconds(seconds);

        // When
        notification.retrySqsPublication(attemptedAt);

        // Then
        if (seconds >= 1800) {
            assertThat(notification.getSqsPublishStatus()).isEqualTo(SqsPublishStatus.EXPIRED);
            assertThat(notification.getNextAttemptAt()).isNull();
            return;
        }
        assertThat(notification.getSqsPublishStatus()).isEqualTo(SqsPublishStatus.PENDING);
        assertThat(notification.getNextAttemptAt())
                .isEqualTo(OCCURRED_AT.plusSeconds(Math.min(seconds + 60, 1800)));
        assertThat(notification.getSqsPublishedAt()).isNull();
    }

    @Test
    @DisplayName("SQS 수락은 수락 시각을 기록하고 다음 발행 시각을 비운다")
    void markSqsPublished_pending_recordsAcceptance() {
        // Given
        Notification notification = Notification.approved(USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT,
                true);
        Instant acceptedAt = OCCURRED_AT.plusSeconds(1);

        // When
        notification.markSqsPublished(acceptedAt);

        // Then
        assertThat(notification.getSqsPublishStatus()).isEqualTo(SqsPublishStatus.PUBLISHED);
        assertThat(notification.getSqsPublishedAt()).isEqualTo(acceptedAt);
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("수락 시각이 없으면 발행 완료로 바꾸지 않는다")
    void markSqsPublished_missingAcceptanceTime_rejectsTransition() {
        // Given
        Notification notification = Notification.approved(USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT,
                true);

        // When & Then
        assertThatThrownBy(() -> notification.markSqsPublished(null))
                .isInstanceOf(BusinessException.class);
        assertThat(notification.getSqsPublishStatus()).isEqualTo(SqsPublishStatus.PENDING);
    }

    @Test
    @DisplayName("영구 오류는 발행 실패로 끝내고 다음 발행 시각을 비운다")
    void failSqsPublication_pending_stopsPublication() {
        // Given
        Notification notification = Notification.approved(USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT,
                true);

        // When
        notification.failSqsPublication();

        // Then
        assertThat(notification.getSqsPublishStatus()).isEqualTo(SqsPublishStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.getSqsPublishedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_REQUIRED", "PUBLISHED", "EXPIRED", "FAILED"})
    @DisplayName("이미 발행을 끝낸 알림은 다시 상태를 바꾸지 못한다")
    void publication_terminalState_rejectsTransitions(String state) {
        // Given
        Notification notification = Notification.approved(USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT,
                !state.equals("NOT_REQUIRED"));
        if (state.equals("PUBLISHED")) {
            notification.markSqsPublished(OCCURRED_AT);
        }
        if (state.equals("EXPIRED")) {
            notification.expireSqsPublication();
        }
        if (state.equals("FAILED")) {
            notification.failSqsPublication();
        }

        // When & Then
        assertThatThrownBy(() -> notification.markSqsPublished(OCCURRED_AT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> notification.retrySqsPublication(OCCURRED_AT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(notification::expireSqsPublication)
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(notification::failSqsPublication).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("승인 알림은 게시물 대상을 기록하고 반려 사유는 갖지 않는다")
    void approved_validInput_setsPostSourceWithoutRejectionReason() {
        // When
        Notification notification = Notification.approved(USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT,
                true);

        // Then
        assertThat(notification.getSourceType()).isEqualTo(NotificationSourceType.POST);
        assertThat(notification.getSourceId()).isEqualTo(POST_ID);
        assertThat(notification.getType()).isEqualTo(NotificationType.POST_APPROVED);
        assertThat(notification.getCreatedAt()).isEqualTo(OCCURRED_AT);
        assertThat(notification.getRejectionReason()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {499, 500})
    @DisplayName("반려 사유는 최대 500자까지 원문을 유지한다")
    void rejected_reasonWithinLimit_preservesReason(int length) {
        // Given
        String reason = "가".repeat(length);

        // When
        Notification notification = Notification.rejected(
                USER_ID, POST_ID, EVENT_KEY, reason, OCCURRED_AT, true);

        // Then
        assertThat(notification.getSourceType()).isEqualTo(NotificationSourceType.POST);
        assertThat(notification.getSourceId()).isEqualTo(POST_ID);
        assertThat(notification.getRejectionReason()).isEqualTo(reason);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("반려 알림은 비어 있는 반려 사유를 거부한다")
    void rejected_missingReason_throwsBusinessException(String reason) {
        // When & Then
        assertThatThrownBy(() -> Notification.rejected(
                USER_ID, POST_ID, EVENT_KEY, reason, OCCURRED_AT, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("반려 사유가 501자이면 알림을 생성하지 않는다")
    void rejected_reasonOverLimit_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> Notification.rejected(
                USER_ID, POST_ID, EVENT_KEY, "가".repeat(501), OCCURRED_AT, true))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @MethodSource("missingRequiredValues")
    @DisplayName("승인과 반려는 회원·게시물·사건·발생 시각 중 하나라도 없으면 생성하지 않는다")
    void create_missingRequiredValue_throwsBusinessException(
            UUID userId,
            UUID postId,
            UUID eventKey,
            Instant occurredAt
    ) {
        // When & Then
        assertThatThrownBy(() -> Notification.approved(userId, postId, eventKey, occurredAt, true))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Notification.rejected(
                userId, postId, eventKey, "사진 품질", occurredAt, true))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("승인·반려는 푸시 필요 여부에 따라 즉시 발행 대기 또는 발행 불필요로 생성한다")
    void create_pushPreference_setsInitialPublicationState(boolean pushEnabled) {
        // When
        Notification approved = Notification.approved(
                USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT, pushEnabled);
        Notification rejected = Notification.rejected(
                USER_ID, POST_ID, EVENT_KEY, "사진 품질", OCCURRED_AT, pushEnabled);

        // Then
        for (Notification notification : new Notification[]{approved, rejected}) {
            assertThat(notification.getSqsPublishStatus()).isEqualTo(pushEnabled
                    ? SqsPublishStatus.PENDING
                    : SqsPublishStatus.NOT_REQUIRED);
            assertThat(notification.getNextAttemptAt()).isEqualTo(pushEnabled ? OCCURRED_AT : null);
            assertThat(notification.getSqsPublishedAt()).isNull();
        }
    }

    private static Stream<Arguments> missingRequiredValues() {
        return Stream.of(
                Arguments.of(null, POST_ID, EVENT_KEY, OCCURRED_AT),
                Arguments.of(USER_ID, null, EVENT_KEY, OCCURRED_AT),
                Arguments.of(USER_ID, POST_ID, null, OCCURRED_AT),
                Arguments.of(USER_ID, POST_ID, EVENT_KEY, null));
    }
}
