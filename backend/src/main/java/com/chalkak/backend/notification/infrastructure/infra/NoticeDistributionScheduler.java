package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.NoticeDistributionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class NoticeDistributionScheduler {

    private final NoticeDistributionService noticeDistributionService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul", scheduler = "notificationNoticeTaskScheduler")
    public void distributeNotices() {
        try {
            noticeDistributionService.distributeNotices();
        } catch (RuntimeException exception) {
            log.atError().addKeyValue("type", "notification")
                    .addKeyValue("stage", "notice_scheduler_failed")
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("공지 배포 대상 조회 실패");
        }
    }
}
