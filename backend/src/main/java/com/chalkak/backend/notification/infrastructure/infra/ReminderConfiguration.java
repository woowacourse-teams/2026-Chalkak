package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.ReminderPushService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 정시 발행과 전용 스레드는 함께 있어야 동작하므로 같은 조건 아래에서 함께 등록한다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "chalkak.notification.reminder", name = "enabled", havingValue = "true")
public class ReminderConfiguration {

    // 회원 수만큼 도는 발행 루프가 주제 발행·공지 배포와 공용 예약 작업을 밀어내지 않게 전용 스레드에서 실행한다.
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler notificationReminderTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-reminder-");
        return scheduler;
    }

    @Bean
    public ReminderScheduler reminderScheduler(ReminderPushService reminderPushService) {
        return new ReminderScheduler(reminderPushService);
    }
}
