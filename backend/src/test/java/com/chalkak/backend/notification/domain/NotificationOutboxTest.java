package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationOutboxTest {

    private static final UUID POST_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    @DisplayName("게시물 검수 대기 알림은 Slack 관리자 검수 대상으로 생성한다")
    void createPostModerationPending_validPost_createsPendingOutbox() {
        // When
        NotificationOutbox outbox = NotificationOutbox.createPostModerationPending(
                POST_ID,
                CREATED_AT
        );

        // Then
        assertThat(outbox.getPostId()).isEqualTo(POST_ID);
        assertThat(outbox.getType()).isEqualTo(NotificationType.POST_MODERATION_PENDING);
        assertThat(outbox.getChannel()).isEqualTo(NotificationChannel.SLACK);
        assertThat(outbox.getTarget())
                .isEqualTo(NotificationTarget.ADMIN_MODERATION_REVIEWERS);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getNextAttemptAt()).isEqualTo(CREATED_AT);
        assertThat(outbox.getProcessingToken()).isNull();
        assertThat(outbox.getLeaseExpiresAt()).isNull();
    }

    @Test
    @DisplayName("알림을 선점하면 처리 토큰과 임대 만료 시각을 기록하고 시도 횟수를 올린다")
    void claim_pendingOutbox_movesToProcessing() {
        // Given
        NotificationOutbox outbox = NotificationOutbox.createPostModerationPending(
                POST_ID,
                CREATED_AT
        );
        UUID processingToken = UUID.randomUUID();
        Duration lease = Duration.ofSeconds(30);

        // When
        NotificationDispatch dispatch = outbox.claim(processingToken, CREATED_AT, lease);

        // Then
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PROCESSING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getProcessingToken()).isEqualTo(processingToken);
        assertThat(outbox.getLeaseExpiresAt()).isEqualTo(CREATED_AT.plus(lease));
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(dispatch.postId()).isEqualTo(POST_ID);
        assertThat(dispatch.processingToken()).isEqualTo(processingToken);
        assertThat(dispatch.attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("유효한 임대 중인 알림은 다시 선점할 수 없다")
    void claim_processingOutboxBeforeLeaseExpiry_throwsBusinessException() {
        // Given
        NotificationOutbox outbox = NotificationOutbox.createPostModerationPending(
                POST_ID,
                CREATED_AT
        );
        outbox.claim(UUID.randomUUID(), CREATED_AT, Duration.ofSeconds(30));

        // When & Then
        assertThatThrownBy(() -> outbox.claim(
                UUID.randomUUID(),
                CREATED_AT.plusSeconds(29),
                Duration.ofSeconds(30)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("아직 처리 중인 알림은 다시 선점할 수 없습니다.");
    }

    @Test
    @DisplayName("임대가 만료된 알림은 새 토큰으로 다시 선점한다")
    void claim_expiredProcessingOutbox_reclaimsWithNewToken() {
        // Given
        NotificationOutbox outbox = NotificationOutbox.createPostModerationPending(
                POST_ID,
                CREATED_AT
        );
        outbox.claim(UUID.randomUUID(), CREATED_AT, Duration.ofSeconds(30));
        UUID newToken = UUID.randomUUID();

        // When
        NotificationDispatch dispatch = outbox.claim(
                newToken,
                CREATED_AT.plusSeconds(30),
                Duration.ofSeconds(30)
        );

        // Then
        assertThat(outbox.getAttemptCount()).isEqualTo(2);
        assertThat(outbox.getProcessingToken()).isEqualTo(newToken);
        assertThat(dispatch.processingToken()).isEqualTo(newToken);
        assertThat(dispatch.attemptCount()).isEqualTo(2);
    }
}
