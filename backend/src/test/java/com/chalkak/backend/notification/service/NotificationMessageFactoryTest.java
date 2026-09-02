package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.post.service.PostModerationPendingEvent;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationMessageFactoryTest {

    @Test
    @DisplayName("게시물 검수 알림은 관리자 웹의 해당 게시물 상세 주소를 만든다")
    void create_postModerationPending_createsAdminPostDetailLink() {
        // Given
        UUID postId = UUID.fromString("0199a000-0000-7000-8000-000000000001");
        Instant createdAt = Instant.parse("2026-09-02T00:00:00Z");
        PostModerationPendingEvent event = new PostModerationPendingEvent(
                postId,
                createdAt
        );
        NotificationMessageFactory factory = new NotificationMessageFactory(
                URI.create("https://admin.example.com")
        );

        // When
        NotificationMessage message = factory.create(event);

        // Then
        assertThat(message.title()).isEqualTo("새 게시물 검수 요청");
        assertThat(message.body()).contains("승인 대기");
        assertThat(message.actionUri()).isEqualTo(URI.create(
                "https://admin.example.com/posts/0199a000-0000-7000-8000-000000000001"
        ));
        assertThat(message.occurredAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("관리자 웹 주소의 마지막 슬래시는 제거한 뒤 상세 경로를 붙인다")
    void create_baseUrlWithTrailingSlash_doesNotDuplicateSlash() {
        // Given
        UUID postId = UUID.fromString("0199a000-0000-7000-8000-000000000001");
        PostModerationPendingEvent event = new PostModerationPendingEvent(
                postId,
                Instant.parse("2026-09-02T00:00:00Z")
        );
        NotificationMessageFactory factory = new NotificationMessageFactory(
                URI.create("https://admin.example.com/")
        );

        // When & Then
        assertThat(factory.create(event).actionUri().toString())
                .isEqualTo("https://admin.example.com/posts/" + postId);
    }
}
