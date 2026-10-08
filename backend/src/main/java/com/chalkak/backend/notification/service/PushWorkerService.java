package com.chalkak.backend.notification.service;

import com.chalkak.backend.auth.service.LoginSessionService;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.domain.SqsPublishStatus;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class PushWorkerService {
    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository deviceRepository;
    private final LoginSessionService loginSessionService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PushWorkerService(
            NotificationRepository notificationRepository,
            PushDeviceRepository deviceRepository,
            LoginSessionService loginSessionService,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.deviceRepository = deviceRepository;
        this.loginSessionService = loginSessionService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PushProcessingResult process(PushMessage message, DevicePushSender sender) {
        if (!isValidMessage(message) || !clock.instant().isBefore(message.expiresAt())) {
            logSkipped(message, null, "INVALID_OR_EXPIRED");
            return PushProcessingResult.completed();
        }
        List<UUID> deviceIds = transactionTemplate
                .execute(status -> deviceRepository.findIdsByUserId(message.userId()));
        boolean accepted = false;
        boolean retryable = false;
        Duration retryAfter = Duration.ZERO;
        for (UUID deviceId : deviceIds) {
            DevicePushResult result = sendToDevice(message, deviceId, sender);
            accepted = accepted || result.isAccepted();
            retryable = retryable || result.isRetryable();
            retryAfter = findLongestRetryAfter(retryAfter, result);
        }
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
            UUID deviceId,
            DevicePushSender sender
    ) {
        DevicePushRequest request = findCurrentRequest(message, deviceId);
        if (request == null || !request.isSendableAt(clock.instant())) {
            logSkipped(message, deviceId, "LATEST_CONDITIONS");
            return DevicePushResult.skipped("LATEST_CONDITIONS");
        }
        DevicePushResult result = sender.send(request);
        logResult(message, deviceId, result);
        if (result.isInvalidToken()) {
            deleteInvalidToken(deviceId, request);
        }
        return result;
    }

    private void deleteInvalidToken(UUID deviceId, DevicePushRequest request) {
        FcmToken token = new FcmToken(request.token());
        transactionTemplate.executeWithoutResult(status -> deviceRepository
                .deleteByIdAndTokenHash(deviceId, token.getHash()));
    }

    private DevicePushRequest findCurrentRequest(PushMessage message, UUID deviceId) {
        try {
            return transactionTemplate.execute(status -> findRequest(message, deviceId));
        } catch (UnauthorizedException exception) {
            return null;
        }
    }

    private DevicePushRequest findRequest(PushMessage message, UUID deviceId) {
        Notification notification = notificationRepository.findDetailByIdAndUserId(
                message.notificationId(), message.userId(), message.occurredAt())
                .map(detail -> detail.notification()).orElse(null);
        if (!isSendableNotification(notification, message)) {
            return null;
        }
        PushDevice device = deviceRepository.findByIdAndUserId(deviceId, message.userId())
                .orElse(null);
        if (device == null) {
            return null;
        }
        User user = loginSessionService.getUsableUser(message.userId(), device.getSessionId(),
                clock.instant());
        if (!user.isModerationPushEnabled()) {
            return null;
        }
        return new DevicePushRequest(message, device.getFcmToken(), notification.getTitle(),
                notification.getBody(), notification.getPushExpiresAt());
    }

    private boolean isSendableNotification(Notification notification, PushMessage message) {
        if (notification == null || notification.getReadAt() != null) {
            return false;
        }
        if (notification.isPushExpired(clock.instant()) || !matches(message, notification)) {
            return false;
        }
        SqsPublishStatus status = notification.getSqsPublishStatus();
        return status == SqsPublishStatus.PENDING || status == SqsPublishStatus.PUBLISHED;
    }

    private boolean isValidMessage(PushMessage message) {
        return message != null && message.notificationId() != null && message.eventId() != null
                && message.userId() != null && message.type() != null
                && message.sourceType() != null && message.sourceId() != null
                && message.occurredAt() != null && message.expiresAt() != null;
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
            event.addKeyValue("eventId", message.eventId())
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
        if (result.isPermanentFailure()) {
            event = log.atError();
        }
        event.addKeyValue("type", "notification").addKeyValue("stage", "fcm_result")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("notificationId", message.notificationId())
                .addKeyValue("deviceId", deviceId).addKeyValue("result", result.status())
                .addKeyValue("errorCode", result.errorCode()).log("FCM 발송 요청 결과");
    }
}
