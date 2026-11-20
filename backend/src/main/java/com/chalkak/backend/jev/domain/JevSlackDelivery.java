package com.chalkak.backend.jev.domain;

import com.chalkak.backend.notification.service.NotificationDeliveryResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** DB에 저장하는 알림 상태. 외부 전송은 트랜잭션 밖에서 하고 token으로 늦은 완료를 구분한다. */
public record JevSlackDelivery(
        String status,
        int attempts,
        UUID token,
        Instant availableAt,
        Instant leaseUntil,
        List<Attempt> history
) {

    public JevSlackDelivery {
        history = List.copyOf(history);
    }

    public static JevSlackDelivery ready(Instant now, boolean enabled) {
        return new JevSlackDelivery(
                enabled ? "PENDING" : "DISABLED",
                0,
                null,
                enabled ? now : null,
                null,
                List.of()
        );
    }

    public JevSlackDelivery refresh(Instant now) {
        if (!"SENDING".equals(status) || leaseUntil.isAfter(now)) {
            return this;
        }
        return finish(
                token,
                new NotificationDeliveryResult(
                        false,
                        true,
                        "DELIVERY_RESULT_LOST",
                        null,
                        null
                ),
                now
        );
    }

    public JevSlackDelivery claim(Instant now, Duration lease) {
        if (availableAt == null || availableAt.isAfter(now)
                || !("PENDING".equals(status) || "RETRY_WAIT".equals(status))) {
            return this;
        }
        UUID nextToken = UUID.randomUUID();
        List<Attempt> next = new ArrayList<>(history);
        next.add(new Attempt(
                nextToken,
                now,
                null,
                null,
                null
        ));
        return new JevSlackDelivery(
                "SENDING",
                attempts + 1,
                nextToken,
                null,
                now.plus(lease),
                next
        );
    }

    public JevSlackDelivery finish(
            UUID receivedToken,
            NotificationDeliveryResult result,
            Instant now
    ) {
        if (!"SENDING".equals(status) || !token.equals(receivedToken)) {
            return this;
        }
        List<Attempt> next = new ArrayList<>(history);
        Attempt previous = next.getLast();
        next.set(next.size() - 1, new Attempt(
                token,
                previous.startedAt(),
                now,
                result.error(),
                result.httpStatus()
        ));
        boolean retry = !result.sent() && result.retryable() && attempts < 3;
        long delay = attempts == 1 ? 60 : 300;
        if (result.retryAfterSeconds() != null) {
            delay = Math.max(delay, Math.min(86400, result.retryAfterSeconds()));
        }
        return new JevSlackDelivery(
                result.sent() ? "SENT" : retry ? "RETRY_WAIT" : "FAILED",
                attempts,
                token,
                retry ? now.plusSeconds(delay) : null,
                null,
                next
        );
    }

    public JevSlackDelivery skip() {
        return new JevSlackDelivery(
                "SKIPPED",
                attempts,
                token,
                null,
                null,
                history
        );
    }

    public Instant dueAt() {
        return "SENDING".equals(status) ? leaseUntil : availableAt;
    }

    public record Attempt(
            UUID token,
            Instant startedAt,
            Instant finishedAt,
            String error,
            Integer httpStatus
    ) {
    }
}
