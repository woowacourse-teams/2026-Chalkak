package com.chalkak.backend.notification.repository;

import java.util.List;

public record NotificationSlice(List<NotificationSummary> notifications, boolean hasNext) {
}
