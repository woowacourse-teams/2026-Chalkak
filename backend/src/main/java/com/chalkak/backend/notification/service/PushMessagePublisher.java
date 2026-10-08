package com.chalkak.backend.notification.service;

public interface PushMessagePublisher {

    PushPublicationResult publish(PushMessage message);
}
