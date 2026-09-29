package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.repository.NotificationDetail;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.NotificationSlice;
import com.chalkak.backend.notification.repository.NotificationSummary;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository notificationJpaRepository;

    @Override
    public Notification save(Notification notification) {
        return notificationJpaRepository.save(notification);
    }

    @Override
    public NotificationSlice findByUserId(UUID userId, int page, int pageSize) {
        Slice<NotificationSummary> slice = notificationJpaRepository.findSummariesByUserId(
                userId,
                PageRequest.of(page - 1, pageSize));
        return new NotificationSlice(slice.getContent(), slice.hasNext());
    }

    @Override
    public Optional<NotificationDetail> findDetailByIdAndUserId(
            UUID notificationId,
            UUID userId
    ) {
        return notificationJpaRepository.findDetailByIdAndUserId(notificationId, userId);
    }

    @Override
    public boolean existsUnreadByUserId(UUID userId) {
        return notificationJpaRepository.existsByUserIdAndReadAtIsNull(userId);
    }

    @Override
    public int markRead(UUID notificationId, UUID userId, Instant readAt) {
        return notificationJpaRepository.markRead(notificationId, userId, readAt);
    }

    @Override
    public void markAllRead(UUID userId, Instant readAt) {
        notificationJpaRepository.markAllRead(userId, readAt);
    }

    @Override
    public void deleteExpiredForActiveUsers(Instant createdBefore) {
        notificationJpaRepository.deleteExpiredForActiveUsers(createdBefore);
    }

    @Override
    public void deleteExpiredForWithdrawnUsers(Instant withdrawnBefore) {
        notificationJpaRepository.deleteExpiredForWithdrawnUsers(withdrawnBefore);
    }
}
