package com.chalkak.backend.notification.service;

import com.chalkak.backend.post.service.PostModerationPendingEvent;
import java.net.URI;

public class NotificationMessageFactory {

    private final String adminWebBaseUrl;

    public NotificationMessageFactory(URI adminWebBaseUrl) {
        String normalized = adminWebBaseUrl.toString();
        this.adminWebBaseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    public NotificationMessage create(PostModerationPendingEvent event) {
        return new NotificationMessage(
                "새 게시물 검수 요청",
                "새 게시물이 승인 대기 상태가 되었습니다.",
                "관리자 웹에서 검수하기",
                URI.create(adminWebBaseUrl + "/posts/" + event.postId()),
                event.occurredAt()
        );
    }
}
