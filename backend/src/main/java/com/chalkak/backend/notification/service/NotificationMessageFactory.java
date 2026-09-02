package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationDispatch;
import com.chalkak.backend.notification.domain.NotificationType;
import java.net.URI;

public class NotificationMessageFactory {

    private final String adminWebBaseUrl;

    public NotificationMessageFactory(URI adminWebBaseUrl) {
        String normalized = adminWebBaseUrl.toString();
        this.adminWebBaseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    public NotificationMessage create(NotificationDispatch dispatch) {
        if (dispatch.type() != NotificationType.POST_MODERATION_PENDING) {
            throw new IllegalArgumentException("지원하지 않는 알림 유형입니다.");
        }
        return new NotificationMessage(
                "새 게시물 검수 요청",
                "새 게시물이 승인 대기 상태가 되었습니다.",
                "관리자 웹에서 검수하기",
                URI.create(adminWebBaseUrl + "/posts/" + dispatch.postId()),
                dispatch.occurredAt()
        );
    }
}
