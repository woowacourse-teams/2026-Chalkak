package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserNotificationService {

    private final NotificationRepository notificationRepository;

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
}
