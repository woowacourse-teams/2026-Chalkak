package com.chalkak.backend.notification.domain;

import com.chalkak.backend.user.domain.User;
import java.util.function.Predicate;

public enum NotificationType {
    POST_APPROVED(NotificationSourceType.POST, User::isModerationPushEnabled), // 게시물 승인
    POST_REJECTED(NotificationSourceType.POST, User::isModerationPushEnabled), // 게시물 반려
    TOPIC(NotificationSourceType.TOPIC, User::isTopicPushEnabled), // 오늘의 주제
    NOTICE(NotificationSourceType.NOTICE, user -> true); // 관리자 공지, 전용 수신 설정 없이 항상 보낸다.

    private final NotificationSourceType sourceType;
    private final Predicate<User> pushEnabled;

    NotificationType(NotificationSourceType sourceType, Predicate<User> pushEnabled) {
        this.sourceType = sourceType;
        this.pushEnabled = pushEnabled;
    }

    public boolean isSourceOf(NotificationSourceType sourceType) {
        return this.sourceType == sourceType;
    }

    public boolean isPushEnabledFor(User user) {
        return pushEnabled.test(user);
    }
}
