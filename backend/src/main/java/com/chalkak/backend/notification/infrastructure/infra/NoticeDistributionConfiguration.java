package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.NoticeDistributionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 배포 작업과 전용 스레드는 함께 있어야 동작하므로 같은 조건 아래에서 함께 등록한다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "chalkak.notification.notice", name = "distribution-enabled", havingValue = "true")
public class NoticeDistributionConfiguration {

    // 공지 배포는 전용 스레드에서 실행해 Relay·주제 발행과 공용 예약 작업을 밀어내지 않는다.
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler notificationNoticeTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-notice-");
        return scheduler;
    }

    @Bean
    public NoticeDistributionScheduler noticeDistributionScheduler(
            NoticeDistributionService noticeDistributionService
    ) {
        return new NoticeDistributionScheduler(noticeDistributionService);
    }
}
