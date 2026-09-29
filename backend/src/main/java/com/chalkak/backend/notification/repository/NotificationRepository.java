package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notification;

public interface NotificationRepository {

    Notification save(Notification notification);
}
