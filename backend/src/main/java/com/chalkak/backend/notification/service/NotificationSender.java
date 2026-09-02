package com.chalkak.backend.notification.service;

/**
 * 외부 전달 기술을 교체하거나 추가할 때 구현하는 포트다.
 */
public interface NotificationSender {

    boolean send(NotificationMessage message);
}
