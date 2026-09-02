package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.notification.domain.NotificationChannel;
import com.chalkak.backend.notification.domain.NotificationTarget;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDispatcherTest {

    private static final NotificationMessage MESSAGE = new NotificationMessage(
            "새 게시물 검수 요청",
            "새 게시물이 승인 대기 상태가 되었습니다.",
            "관리자 웹에서 검수하기",
            URI.create("https://admin.example.com/posts/0199a000-0000-7000-8000-000000000001"),
            Instant.parse("2026-09-02T00:00:00Z")
    );

    @Test
    @DisplayName("알림 채널을 지원하는 발송 구현에 메시지를 전달한다")
    void dispatch_supportedChannel_usesMatchingSender() {
        // Given
        RecordingSender sender = new RecordingSender(NotificationChannel.SLACK);
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(sender));

        // When
        NotificationSendResult result = dispatcher.dispatch(
                NotificationChannel.SLACK,
                MESSAGE
        );

        // Then
        assertThat(result.outcome()).isEqualTo(NotificationSendOutcome.SENT);
        assertThat(sender.sentMessage).isEqualTo(MESSAGE);
    }

    @Test
    @DisplayName("같은 채널의 발송 구현이 둘이면 시작 단계에서 실패한다")
    void create_duplicateChannelSenders_throwsIllegalStateException() {
        // Given
        NotificationSender first = new RecordingSender(NotificationChannel.SLACK);
        NotificationSender second = new RecordingSender(NotificationChannel.SLACK);

        // When & Then
        assertThatThrownBy(() -> new NotificationDispatcher(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("같은 알림 채널의 발송 구현을 중복 등록할 수 없습니다: SLACK");
    }

    @Test
    @DisplayName("등록되지 않은 채널은 외부 예외 없이 영구 실패 결과로 바꾼다")
    void dispatch_withoutSender_returnsPermanentFailure() {
        // Given
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of());

        // When
        NotificationSendResult result = dispatcher.dispatch(
                NotificationChannel.SLACK,
                MESSAGE
        );

        // Then
        assertThat(result.outcome()).isEqualTo(NotificationSendOutcome.PERMANENT_FAILURE);
        assertThat(result.failureCode()).isEqualTo("NOTIFICATION_CHANNEL_UNSUPPORTED");
    }

    private static final class RecordingSender implements NotificationSender {

        private final NotificationChannel channel;
        private NotificationMessage sentMessage;

        private RecordingSender(NotificationChannel channel) {
            this.channel = channel;
        }

        @Override
        public NotificationChannel supportedChannel() {
            return channel;
        }

        @Override
        public NotificationSendResult send(NotificationMessage message) {
            this.sentMessage = message;
            return NotificationSendResult.sent();
        }
    }
}
