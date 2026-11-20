package com.chalkak.backend.jev.infrastructure.infra;

import com.chalkak.backend.jev.service.JevSlackService;
import com.chalkak.backend.notification.service.NotificationDeliveryResult;
import com.chalkak.backend.notification.service.NotificationMessageFactory;
import com.chalkak.backend.notification.service.NotificationSender;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chalkak.admin.notification", name = "delivery-enabled", havingValue = "true")
public class JevSlackDispatcher {

    private final JevSlackService service;
    private final NotificationMessageFactory messages;
    private final NotificationSender sender;

    @Scheduled(fixedDelay = 1000, scheduler = "jevSlackTaskScheduler")
    public void dispatch() {
        for (UUID id : service.findDueIds()) {
            try {
                deliver(id);
            } catch (RuntimeException exception) {
                log.warn(
                        "Jev Slack 처리 오류: runId={}, errorType={}",
                        id,
                        exception.getClass().getSimpleName()
                );
            }
        }
    }

    public void deliver(UUID id) {
        var claim = service.claim(id);
        if (claim == null) {
            return;
        }
        NotificationDeliveryResult result;
        try {
            result = sender.deliver(messages.createReview(
                    claim.postId(),
                    claim.title(),
                    claim.summary(),
                    claim.occurredAt()
            ));
        } catch (RuntimeException exception) {
            result = null;
        }
        if (result == null) {
            result = new NotificationDeliveryResult(
                    false,
                    true,
                    "DELIVERY_RESULT_UNKNOWN",
                    null,
                    null
            );
        }
        service.finish(
                id,
                claim.token(),
                result
        );
    }
}
