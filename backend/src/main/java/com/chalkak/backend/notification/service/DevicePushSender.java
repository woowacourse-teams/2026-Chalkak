package com.chalkak.backend.notification.service;

public interface DevicePushSender {
    DevicePushResult send(DevicePushRequest request);
}
