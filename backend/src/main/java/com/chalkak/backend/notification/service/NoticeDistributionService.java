package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class NoticeDistributionService {

    private static final int BATCH_SIZE = 10;

    private final NoticeRepository noticeRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public NoticeDistributionService(
            NoticeRepository noticeRepository,
            NotificationRepository notificationRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.noticeRepository = noticeRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void distributeNotices() {
        for (UUID noticeId : noticeRepository.findDistributableIds(clock.instant(), BATCH_SIZE)) {
            try {
                transactionTemplate.executeWithoutResult(status -> distribute(noticeId));
            } catch (RuntimeException exception) {
                // 실패한 공지는 예약 상태로 남아 다음 확인에서 다시 시도한다.
                log.atError().addKeyValue("type", "notification")
                        .addKeyValue("stage", "notice_distribution_failed")
                        .addKeyValue("noticeId", noticeId)
                        .addKeyValue("errorCode", exception.getClass().getSimpleName())
                        .log("공지 배포 실패");
            }
        }
    }

    // 회원별 알림 생성과 배포 완료 표시를 한 트랜잭션으로 묶어 공지당 한 번만 배포한다.
    private void distribute(UUID noticeId) {
        Instant now = clock.instant();
        Notice notice = noticeRepository.findDistributableForUpdate(noticeId, now).orElse(null);
        if (notice == null) {
            return;
        }
        int recipientCount = notificationRepository.createNoticeNotifications(notice, now);
        notice.distribute(now);
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "notice_distributed")
                .addKeyValue("noticeId", noticeId)
                .addKeyValue("recipientCount", recipientCount)
                .log("공지 배포 완료");
    }
}
