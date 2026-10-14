package com.chalkak.backend.notification.service;

import com.chalkak.backend.auth.service.token.LoginSessionService;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PublishStatus;
import com.chalkak.backend.notification.domain.PushDeadlinePolicy;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class PushWorkerService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String TOPIC_PUSH_TITLE = "오늘의 주제";
    private final NotificationRepository notificationRepository;
    private final TopicRepository topicRepository;
    private final PostRepository postRepository;
    private final PushDeviceRepository deviceRepository;
    private final LoginSessionService loginSessionService;
    private final DevicePushSender sender;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PushWorkerService(
            NotificationRepository notificationRepository,
            TopicRepository topicRepository,
            PostRepository postRepository,
            PushDeviceRepository deviceRepository,
            LoginSessionService loginSessionService,
            DevicePushSender sender,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.topicRepository = topicRepository;
        this.postRepository = postRepository;
        this.deviceRepository = deviceRepository;
        this.loginSessionService = loginSessionService;
        this.sender = sender;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PushProcessingResult process(PushMessage message) {
        if (!isValidMessage(message)) {
            logSkipped(message, null, "INVALID_MESSAGE");
            return PushProcessingResult.completed();
        }
        if (PushDeadlinePolicy.isExpiredAt(message.expiresAt(), clock.instant())) {
            logSkipped(message, null, "EXPIRED");
            return PushProcessingResult.completed();
        }
        List<UUID> deviceIds = transactionTemplate
                .execute(status -> deviceRepository.findIdsByUserId(message.userId()));
        if (deviceIds.isEmpty()) {
            logSkipped(message, null, "NO_DEVICES");
        }
        Map<DevicePushResult.Status, Integer> counts = new EnumMap<>(DevicePushResult.Status.class);
        boolean accepted = false;
        boolean retryable = false;
        Duration retryAfter = Duration.ZERO;
        for (UUID deviceId : deviceIds) {
            DevicePushResult result = sendToDevice(message, deviceId);
            counts.merge(result.status(), 1, Integer::sum);
            accepted = accepted || result.isAccepted();
            retryable = retryable || result.isRetryable();
            retryAfter = findLongestRetryAfter(retryAfter, result);
        }
        logSummary(message, deviceIds.size(), counts, !accepted && retryable);
        if (accepted) {
            return PushProcessingResult.completed();
        }
        return new PushProcessingResult(retryable, retryAfter);
    }

    private Duration findLongestRetryAfter(Duration current, DevicePushResult result) {
        if (!result.isRetryable()) {
            return current;
        }
        Duration candidate = result.retryAfter();
        if (candidate.compareTo(current) > 0) {
            return candidate;
        }
        return current;
    }

    private DevicePushResult sendToDevice(
            PushMessage message,
            UUID deviceId
    ) {
        try {
            return sendToCurrentDevice(message, deviceId);
        } catch (RuntimeException exception) {
            logDeviceFailure(message, deviceId, "worker_device_failed", exception);
            return DevicePushResult.retryable(exception.getClass().getSimpleName());
        }
    }

    private DevicePushResult sendToCurrentDevice(
            PushMessage message,
            UUID deviceId
    ) {
        DevicePushRequest request = findCurrentRequest(message, deviceId);
        if (request == null) {
            return DevicePushResult.skipped("LATEST_CONDITIONS");
        }
        DevicePushResult result = sender.send(request);
        if (result.status() == DevicePushResult.Status.SKIPPED) {
            logSkipped(message, deviceId, result.errorCode());
            return result;
        }
        logResult(message, deviceId, result);
        if (result.isInvalidToken()) {
            deleteInvalidToken(message, deviceId, request);
        }
        return result;
    }

    private void deleteInvalidToken(
            PushMessage message,
            UUID deviceId,
            DevicePushRequest request
    ) {
        try {
            FcmToken token = new FcmToken(request.token());
            transactionTemplate.executeWithoutResult(status -> deviceRepository
                    .deleteByIdAndTokenHash(deviceId, token.getHash()));
        } catch (RuntimeException exception) {
            logDeviceFailure(message, deviceId, "worker_invalid_token_cleanup_failed", exception);
        }
    }

    private DevicePushRequest findCurrentRequest(PushMessage message, UUID deviceId) {
        return transactionTemplate.execute(status -> findRequest(message, deviceId));
    }

    private DevicePushRequest findRequest(PushMessage message, UUID deviceId) {
        PushDevice device = deviceRepository.findByIdAndUserId(deviceId, message.userId())
                .orElse(null);
        if (device == null) {
            return skipRequest(message, deviceId, "DEVICE_MISSING");
        }
        User user = loginSessionService
                .findUsableUser(message.userId(), device.getSessionId(), clock.instant())
                .orElse(null);
        if (user == null) {
            return skipRequest(message, deviceId, "LOGIN_UNUSABLE");
        }
        if (message.type() == NotificationType.TOPIC) {
            return findTopicRequest(message, device, user);
        }
        return findModerationRequest(message, device, user);
    }

    private DevicePushRequest findTopicRequest(
            PushMessage message,
            PushDevice device,
            User user
    ) {
        if (!user.isActive()) {
            return skipRequest(message, device.getId(), "USER_INACTIVE");
        }
        if (!user.isTopicPushEnabled()) {
            return skipRequest(message, device.getId(), "PUSH_DISABLED");
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        Topic topic = topicRepository.findActiveByTopicDate(today).orElse(null);
        if (topic == null || !topic.getId().equals(message.sourceId())) {
            return skipRequest(message, device.getId(), "TOPIC_UNAVAILABLE");
        }
        if (postRepository.findActiveByAuthorIdAndTopicId(user.getId(), topic.getId())
                .isPresent()) {
            return skipRequest(message, device.getId(), "ALREADY_PARTICIPATED");
        }
        return new DevicePushRequest(message, device.getFcmToken(), TOPIC_PUSH_TITLE,
                topic.getTitle(),
                message.expiresAt());
    }

    private DevicePushRequest findModerationRequest(
            PushMessage message,
            PushDevice device,
            User user
    ) {
        if (!user.isModerationPushEnabled()) {
            return skipRequest(message, device.getId(), "PUSH_DISABLED");
        }
        Notification notification = notificationRepository
                .findForPushByIdAndUserId(message.notificationId(), message.userId()).orElse(null);
        if (notification == null) {
            return skipRequest(message, device.getId(), "NOTIFICATION_UNAVAILABLE");
        }
        if (notification.getReadAt() != null) {
            return skipRequest(message, device.getId(), "ALREADY_READ");
        }
        if (notification.isPushExpired(clock.instant())) {
            return skipRequest(message, device.getId(), "EXPIRED");
        }
        if (!matches(message, notification)) {
            return skipRequest(message, device.getId(), "MESSAGE_MISMATCH");
        }
        PublishStatus status = notification.getPublishStatus();
        if (status != PublishStatus.PENDING && status != PublishStatus.PUBLISHED) {
            return skipRequest(message, device.getId(), "PUBLICATION_INACTIVE");
        }
        return new DevicePushRequest(message, device.getFcmToken(), notification.getTitle(),
                notification.getBody(), notification.getPushExpiresAt());
    }

    private DevicePushRequest skipRequest(
            PushMessage message,
            UUID deviceId,
            String reason
    ) {
        logSkipped(message, deviceId, reason);
        return null;
    }

    private boolean isValidMessage(PushMessage message) {
        if (message == null || message.eventId() == null || message.userId() == null
                || message.type() == null || message.sourceType() == null
                || message.sourceId() == null
                || message.occurredAt() == null || message.expiresAt() == null) {
            return false;
        }
        if (message.type() == NotificationType.TOPIC) {
            return message.notificationId() == null
                    && message.sourceType() == NotificationSourceType.TOPIC
                    && !message.occurredAt().isAfter(clock.instant())
                    && PushDeadlinePolicy.isValidLifetime(message.occurredAt(),
                            message.expiresAt());
        }
        return message.notificationId() != null
                && message.sourceType() == NotificationSourceType.POST;
    }

    private boolean matches(PushMessage message, Notification notification) {
        return Objects.equals(message.eventId(), notification.getEventKey())
                && message.type() == notification.getType()
                && message.sourceType() == notification.getSourceType()
                && Objects.equals(message.sourceId(), notification.getSourceId())
                && message.occurredAt().equals(notification.getCreatedAt())
                && message.expiresAt().equals(notification.getPushExpiresAt());
    }

    private void logSkipped(
            PushMessage message,
            UUID deviceId,
            String reason
    ) {
        var event = log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_skipped").addKeyValue("deviceId", deviceId)
                .addKeyValue("reason", reason);
        if (message != null) {
            event.addKeyValue("userId", message.userId())
                    .addKeyValue("notificationType", message.type())
                    .addKeyValue("sourceId", message.sourceId())
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("notificationId", message.notificationId());
        }
        event.log("푸시 발송 조건 미충족");
    }

    private void logResult(
            PushMessage message,
            UUID deviceId,
            DevicePushResult result
    ) {
        var event = log.atInfo();
        if (result.isRetryable() || result.isInvalidToken()) {
            event = log.atWarn();
        }
        if (result.isPermanentFailure()) {
            event = log.atError();
        }
        event.addKeyValue("type", "notification").addKeyValue("stage", "fcm_result")
                .addKeyValue("userId", message.userId())
                .addKeyValue("notificationType", message.type())
                .addKeyValue("sourceId", message.sourceId())
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("notificationId", message.notificationId())
                .addKeyValue("deviceId", deviceId).addKeyValue("result", result.status())
                .addKeyValue("errorCode", result.errorCode()).log("FCM 발송 요청 결과");
    }

    private void logSummary(
            PushMessage message,
            int targetCount,
            Map<DevicePushResult.Status, Integer> counts,
            boolean retryable
    ) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_summary")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("notificationId", message.notificationId())
                .addKeyValue("userId", message.userId())
                .addKeyValue("notificationType", message.type())
                .addKeyValue("targetCount", targetCount)
                .addKeyValue("acceptedCount",
                        counts.getOrDefault(DevicePushResult.Status.ACCEPTED, 0))
                .addKeyValue("retryableCount",
                        counts.getOrDefault(DevicePushResult.Status.RETRYABLE, 0))
                .addKeyValue("permanentFailureCount",
                        counts.getOrDefault(DevicePushResult.Status.PERMANENT_FAILURE, 0))
                .addKeyValue("invalidTokenCount",
                        counts.getOrDefault(DevicePushResult.Status.INVALID_TOKEN, 0))
                .addKeyValue("skippedCount",
                        counts.getOrDefault(DevicePushResult.Status.SKIPPED, 0))
                .addKeyValue("retryable", retryable)
                .log("푸시 기기 처리 결과 집계");
    }

    private void logDeviceFailure(
            PushMessage message,
            UUID deviceId,
            String stage,
            RuntimeException exception
    ) {
        log.atError().addKeyValue("type", "notification").addKeyValue("stage", stage)
                .addKeyValue("userId", message.userId())
                .addKeyValue("notificationType", message.type())
                .addKeyValue("sourceId", message.sourceId())
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("notificationId", message.notificationId())
                .addKeyValue("deviceId", deviceId)
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 기기 처리 오류");
    }
}
