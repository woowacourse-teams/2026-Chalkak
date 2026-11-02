package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.post.service.PostModerationPendingEvent;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PostModerationPendingNotificationListenerTest {

    private static final UUID POST_ID = UUID.fromString("0199a000-0000-7000-8000-000000000001");
    private static final PostModerationPendingEvent EVENT = new PostModerationPendingEvent(
            POST_ID,
            Instant.parse("2026-09-02T00:00:00Z")
    );
    private static final NotificationMessage MESSAGE = new NotificationMessage(
            "새 게시물 검수 요청",
            "새 게시물이 승인 대기 상태가 되었습니다.",
            "관리자 웹에서 검수하기",
            URI.create("https://admin.example.com/posts/" + POST_ID),
            EVENT.occurredAt()
    );

    @Test
    @DisplayName("검수 이벤트로 만든 메시지를 Slack 발송기에 전달한다")
    void sendPostModerationPending_event_sendsMessage() {
        // Given
        NotificationMessageFactory messageFactory = mock(NotificationMessageFactory.class);
        NotificationSender sender = mock(NotificationSender.class);
        given(messageFactory.create(EVENT)).willReturn(MESSAGE);
        given(sender.send(MESSAGE)).willReturn(true);
        PostModerationPendingNotificationListener listener = new PostModerationPendingNotificationListener(
                messageFactory, sender);

        // When
        listener.sendPostModerationPending(EVENT);

        // Then
        then(sender).should().send(MESSAGE);
    }

    @Test
    @DisplayName("Slack이 실패를 반환해도 게시물 처리 흐름으로 예외를 전파하지 않는다")
    void sendPostModerationPending_senderReturnsFalse_doesNotThrow() {
        // Given
        NotificationMessageFactory messageFactory = mock(NotificationMessageFactory.class);
        NotificationSender sender = mock(NotificationSender.class);
        given(messageFactory.create(EVENT)).willReturn(MESSAGE);
        given(sender.send(MESSAGE)).willReturn(false);
        PostModerationPendingNotificationListener listener = new PostModerationPendingNotificationListener(
                messageFactory, sender);

        // When & Then
        assertThatCode(() -> listener.sendPostModerationPending(EVENT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Slack 발송 경계에서 런타임 예외가 나도 외부로 전파하지 않는다")
    void sendPostModerationPending_senderThrows_doesNotExposeSecret(CapturedOutput output) {
        // Given
        NotificationMessageFactory messageFactory = mock(NotificationMessageFactory.class);
        NotificationSender sender = mock(NotificationSender.class);
        given(messageFactory.create(EVENT)).willReturn(MESSAGE);
        given(sender.send(MESSAGE)).willThrow(new IllegalStateException(
                "https://hooks.slack.com/services/SECRET/SECRET/SECRET"
        ));
        PostModerationPendingNotificationListener listener = new PostModerationPendingNotificationListener(
                messageFactory, sender);

        // When & Then
        assertThatCode(() -> listener.sendPostModerationPending(EVENT))
                .doesNotThrowAnyException();
        assertThat(output)
                .contains("NOTIFICATION_DELIVERY_ERROR")
                .doesNotContain("hooks.slack.com")
                .doesNotContain("SECRET/SECRET/SECRET");
    }

}
