package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notification;

public record NotificationDetail(
        Notification notification,
        String originalStorageKey) {
}
