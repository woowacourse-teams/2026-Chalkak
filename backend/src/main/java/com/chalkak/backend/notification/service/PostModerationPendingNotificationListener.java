package com.chalkak.backend.notification.service;

import com.chalkak.backend.post.service.PostModerationPendingEvent;
import lombok.extern.slf4j.Slf4j;
import com.chalkak.backend.jev.repository.JevJobRepository;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 게시물 커밋이 끝난 뒤 관리자 알림을 외부 공급자에게 전달한다. */
@Slf4j
public class PostModerationPendingNotificationListener {

    private final NotificationMessageFactory messageFactory;
    private final NotificationSender sender;
    private final JevJobRepository jobs;

    public PostModerationPendingNotificationListener(
            NotificationMessageFactory messageFactory,
            NotificationSender sender,
            JevJobRepository jobs
    ) {
        this.messageFactory = messageFactory;
        this.sender = sender;
        this.jobs = jobs;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPostModerationPending(PostModerationPendingEvent event) {
        // 신규 등록의 수동 대상도 DB 알림으로 넘기고, 기존 작업 없는 이벤트만 종전 경로를 쓴다.
        if (jobs.findProductionRunByPostId(event.postId()).isPresent()) {
            return;
        }
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
