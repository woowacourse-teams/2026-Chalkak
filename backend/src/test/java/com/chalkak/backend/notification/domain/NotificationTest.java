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

    @Test
    @DisplayName("승인 알림은 게시물 대상을 기록하고 반려 사유는 갖지 않는다")
    void approved_validInput_setsPostSourceWithoutRejectionReason() {
        // When
        Notification notification = Notification.approved(USER_ID, POST_ID, EVENT_KEY, OCCURRED_AT);

        // Then
        assertThat(notification.getSourceType()).isEqualTo(NotificationSourceType.POST);
        assertThat(notification.getSourceId()).isEqualTo(POST_ID);
        assertThat(notification.getType()).isEqualTo(NotificationType.POST_APPROVED);
        assertThat(notification.getCreatedAt()).isEqualTo(OCCURRED_AT);
        assertThat(notification.getPayload()).isNull();
        assertThat(notification.getRejectionReason()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {499, 500})
    @DisplayName("반려 사유는 최대 500자까지 JSON에 원문으로 저장한다")
    void rejected_reasonWithinLimit_preservesReasonInPayload(int length) {
        // Given
        String reason = "가".repeat(length);

        // When
        Notification notification = Notification.rejected(
                USER_ID, POST_ID, EVENT_KEY, reason, OCCURRED_AT);

        // Then
        assertThat(notification.getSourceType()).isEqualTo(NotificationSourceType.POST);
        assertThat(notification.getSourceId()).isEqualTo(POST_ID);
        assertThat(notification.getPayload()).containsEntry("rejectionReason", reason);
        assertThat(notification.getRejectionReason()).isEqualTo(reason);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("반려 알림은 비어 있는 반려 사유를 거부한다")
    void rejected_missingReason_throwsBusinessException(String reason) {
        // When & Then
        assertThatThrownBy(() -> Notification.rejected(
                USER_ID, POST_ID, EVENT_KEY, reason, OCCURRED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("반려 사유가 501자이면 알림을 생성하지 않는다")
    void rejected_reasonOverLimit_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> Notification.rejected(
                USER_ID, POST_ID, EVENT_KEY, "가".repeat(501), OCCURRED_AT))
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
        assertThatThrownBy(() -> Notification.approved(userId, postId, eventKey, occurredAt))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Notification.rejected(
                userId, postId, eventKey, "사진 품질", occurredAt))
                .isInstanceOf(BusinessException.class);
    }

    private static Stream<Arguments> missingRequiredValues() {
        return Stream.of(
                Arguments.of(null, POST_ID, EVENT_KEY, OCCURRED_AT),
                Arguments.of(USER_ID, null, EVENT_KEY, OCCURRED_AT),
                Arguments.of(USER_ID, POST_ID, null, OCCURRED_AT),
                Arguments.of(USER_ID, POST_ID, EVENT_KEY, null));
    }
}
