package com.chalkak.backend.notification.service;

import com.chalkak.backend.post.service.PostModerationPendingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 게시물 커밋이 끝난 뒤 관리자 알림을 외부 공급자에게 전달한다. */
@Slf4j
public class PostModerationPendingNotificationListener {

    private final NotificationMessageFactory messageFactory;
    private final NotificationSender sender;

    public PostModerationPendingNotificationListener(
            NotificationMessageFactory messageFactory,
            NotificationSender sender
    ) {
        this.messageFactory = messageFactory;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPostModerationPending(PostModerationPendingEvent event) {
        try {
            NotificationMessage message = messageFactory.create(event);
            if (!sender.send(message)) {
                log.warn(
                        "관리자 알림 발송 실패: postId={}, failureCode={}",
                        event.postId(),
                        "NOTIFICATION_DELIVERY_FAILED"
                );
            }
        } catch (RuntimeException exception) {
            // Webhook 주소나 공급자 응답이 예외에 포함될 수 있어 예외 원문은 기록하지 않는다.
            log.warn(
                    "관리자 알림 발송 오류: postId={}, failureCode={}",
                    event.postId(),
                    "NOTIFICATION_DELIVERY_ERROR"
            );
        }
    }
}
