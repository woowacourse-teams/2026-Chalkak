package com.chalkak.backend.notification.repository;

import java.time.Instant;

public record PublicationBacklog(long pendingCount, Instant oldestCreatedAt) {
}
