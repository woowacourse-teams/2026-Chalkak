package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.PushDeadlinePolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 한 회차의 회원별 메시지를 SQS에 직접 발행한다. 실패·결과 불명도 재발행하지 않는다(Best Effort).
@Component
@Slf4j
@RequiredArgsConstructor
public class DirectPushPublisher {

    private final PushMessagePublisher publisher;
    private final Clock clock;

    // 발행 도중 원래 예정 시각의 30분 기한에 도달하면 남은 회원은 기한 생략으로 센다.
    public DirectPublicationSummary publish(DirectPushOccurrence occurrence, List<UUID> userIds) {
        Instant expiresAt = PushDeadlinePolicy.calculateExpiresAt(occurrence.occurredAt());
        int accepted = 0;
        int failed = 0;
        int unknown = 0;
        int expired = 0;
        for (UUID userId : userIds) {
            if (PushDeadlinePolicy.isExpiredAt(expiresAt, clock.instant())) {
                expired = userIds.size() - accepted - failed - unknown;
                break;
            }
            PushPublicationResult result = publishMessage(
                    occurrence.toMessage(userId, expiresAt),
                    occurrence.stagePrefix()
            );
            if (result.isAccepted()) {
                accepted++;
                continue;
            }
            if (result.isAcceptanceUnknown()) {
                unknown++;
                continue;
            }
            failed++;
        }
        return new DirectPublicationSummary(
                expiresAt,
                userIds.size(),
                accepted,
                failed,
                unknown,
                expired
        );
    }

    private PushPublicationResult publishMessage(PushMessage message, String stagePrefix) {
        try {
            PushPublicationResult result = publisher.publish(message);
            if (result.isAccepted()) {
                return result;
            }
            log.atWarn().addKeyValue("type", "notification")
                    .addKeyValue("stage", stagePrefix + "_publication_result")
                    .addKeyValue("notificationType", message.type())
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("userId", message.userId())
                    .addKeyValue("sourceId", message.sourceId())
                    .addKeyValue("occurredAt", message.occurredAt())
                    .addKeyValue("result", getPublicationStatus(result))
                    .addKeyValue("errorCode", result.errorCode())
                    .log("푸시 SQS 직접 발행 결과");
            return result;
        } catch (RuntimeException exception) {
            log.atError().addKeyValue("type", "notification")
                    .addKeyValue("stage", stagePrefix + "_publication_unknown")
                    .addKeyValue("notificationType", message.type())
                    .addKeyValue("sourceId", message.sourceId())
                    .addKeyValue("occurredAt", message.occurredAt())
                    .addKeyValue("result", "UNKNOWN")
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("userId", message.userId())
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("푸시 SQS 직접 발행 결과 확인 실패");
            return PushPublicationResult.unknown("SQS_ACCEPTANCE_UNKNOWN");
        }
    }

    private String getPublicationStatus(PushPublicationResult result) {
        if (result.isAcceptanceUnknown()) {
            return "UNKNOWN";
        }
        return "FAILED";
    }
}
