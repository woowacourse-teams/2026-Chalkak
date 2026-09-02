package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationChannel;

/**
 * 외부 전달 기술을 교체하거나 추가할 때 구현하는 포트다.
 */
public interface NotificationSender {

    NotificationChannel supportedChannel();

    NotificationSendResult send(NotificationMessage message);
}
