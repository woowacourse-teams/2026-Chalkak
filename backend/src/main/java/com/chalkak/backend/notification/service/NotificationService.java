package com.chalkak.backend.notification.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.repository.NotificationDetail;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.NotificationSlice;
import com.chalkak.backend.notification.repository.NotificationSummary;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ImageUrlProvider imageUrlProvider;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createForModeration(
            Post post,
            UUID eventKey,
            ModerationStatus status,
            String rejectionReason,
            Instant occurredAt
    ) {
        UUID userId = post.getAuthor().getId();
        UUID postId = post.getId();
        if (status == ModerationStatus.APPROVED) {
            notificationRepository
                    .save(Notification.approved(userId, postId, eventKey, occurredAt));
            return;
        }
        notificationRepository.save(Notification.rejected(
                userId,
                postId,
                eventKey,
                rejectionReason,
                occurredAt));
    }

    public NotificationListResult getNotifications(UUID userId, int page, int pageSize) {
        NotificationSlice slice = notificationRepository.findByUserId(userId, page, pageSize);
        List<NotificationListResult.Summary> summaries = new ArrayList<>(
                slice.notifications().size());
        for (NotificationSummary summary : slice.notifications()) {
            summaries.add(toSummary(summary));
        }
        return new NotificationListResult(
                page,
                pageSize,
                slice.hasNext(),
                summaries);
    }

    public NotificationDetailResult getNotification(UUID userId, UUID notificationId) {
        NotificationDetail detail = notificationRepository
                .findDetailByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "알림을 찾을 수 없습니다."));
        Notification notification = detail.notification();
        return new NotificationDetailResult(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                imageUrlProvider.getUrl(detail.originalStorageKey()),
                notification.getRejectionReason(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    public boolean hasUnreadNotification(UUID userId) {
        return notificationRepository.existsUnreadByUserId(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        int updated = notificationRepository.markRead(notificationId, userId, Instant.now());
        if (updated == 0) {
            throw new NotFoundException(ErrorCode.BUSINESS_ERROR, "알림을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    private NotificationListResult.Summary toSummary(NotificationSummary summary) {
        Notification notification = summary.notification();
        return new NotificationListResult.Summary(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                imageUrlProvider.getUrl(summary.thumbnailStorageKey()),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
