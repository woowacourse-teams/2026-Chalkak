package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.ReminderPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ReminderPushService reminderPushService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul", scheduler = "notificationReminderTaskScheduler")
    public void publishDueReminders() {
        try {
            reminderPushService.publishDueReminders();
        } catch (RuntimeException exception) {
            log.atError().addKeyValue("type", "notification")
                    .addKeyValue("stage", "reminder_scheduler_failed")
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("리마인더 목록 조회 실패");
        }
    }
}
