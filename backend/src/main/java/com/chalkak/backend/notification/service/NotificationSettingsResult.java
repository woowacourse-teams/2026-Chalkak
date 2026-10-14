package com.chalkak.backend.notification.service;

public record NotificationSettingsResult(
        boolean topicPushEnabled,
        boolean moderationPushEnabled) {
}
