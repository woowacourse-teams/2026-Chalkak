package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.repository.NotificationDetail;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.NotificationSlice;
import com.chalkak.backend.notification.repository.NotificationSummary;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
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
    public List<UUID> findDuePublicationIds(Instant now, int limit) {
        return notificationJpaRepository.findDuePublicationIds(now, limit);
    }

    @Override
    public Optional<Notification> findPendingPublicationForUpdate(UUID notificationId,
            Instant now) {
        return notificationJpaRepository.findPendingPublicationForUpdate(notificationId, now);
    }

    @Override
    public Optional<Notification> findForPushByIdAndUserId(UUID notificationId, UUID userId) {
        return notificationJpaRepository.findForPushByIdAndUserId(notificationId, userId);
    }

    @Override
    public Notification save(Notification notification) {
        return notificationJpaRepository.save(notification);
    }

    @Override
    public NotificationSlice findByUserId(
            UUID userId,
            int page,
            int pageSize,
            Instant createdFrom
    ) {
        Slice<NotificationSummary> slice = notificationJpaRepository.findSummariesByUserId(
                userId,
                createdFrom,
                PageRequest.of(page - 1, pageSize));
        return new NotificationSlice(slice.getContent(), slice.hasNext());
    }

    @Override
    public Optional<NotificationDetail> findDetailByIdAndUserId(
            UUID notificationId,
            UUID userId,
            Instant createdFrom
    ) {
        return notificationJpaRepository.findDetailByIdAndUserId(notificationId, userId,
                createdFrom);
    }

    @Override
    public boolean existsUnreadByUserId(UUID userId, Instant createdFrom) {
        return notificationJpaRepository.existsVisibleUnreadByUserId(userId, createdFrom);
    }

    @Override
    public int markRead(
            UUID notificationId,
            UUID userId,
            Instant readAt,
            Instant createdFrom
    ) {
        return notificationJpaRepository.markRead(notificationId, userId, readAt, createdFrom);
    }

    @Override
    public void markAllRead(
            UUID userId,
            Instant readAt,
            Instant createdFrom
    ) {
        notificationJpaRepository.markAllRead(userId, readAt, createdFrom);
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
