package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.notification.service.NoticeDistributionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class NoticeDistributionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NoticeDistributionConfiguration.class)
            .withBean(NoticeDistributionService.class,
                    () -> mock(NoticeDistributionService.class));

    @Test
    @DisplayName("공지 배포가 꺼져 있으면 스케줄러와 전용 스레드를 만들지 않는다")
    void configuration_disabled_doesNotCreateScheduler() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.notification.notice.distribution-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NoticeDistributionScheduler.class);
                    assertThat(context).doesNotHaveBean("notificationNoticeTaskScheduler");
                });
    }

    @Test
    @DisplayName("공지 배포를 켜면 스케줄러와 공지 전용 단일 스레드를 만든다")
    void configuration_enabled_createsDedicatedScheduler() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.notification.notice.distribution-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NoticeDistributionScheduler.class);
                    ThreadPoolTaskScheduler scheduler = context.getBean(
                            "notificationNoticeTaskScheduler", ThreadPoolTaskScheduler.class);
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                            .isEqualTo(1);
                    assertThat(scheduler.getThreadNamePrefix()).isEqualTo("notification-notice-");
                });
    }
}
