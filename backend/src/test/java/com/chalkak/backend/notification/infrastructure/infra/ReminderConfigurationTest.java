package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.notification.service.ReminderPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class ReminderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReminderConfiguration.class)
            .withBean(ReminderPushService.class, () -> mock(ReminderPushService.class));

    @Test
    @DisplayName("리마인더 발행이 꺼져 있으면 스케줄러와 전용 스레드를 만들지 않는다")
    void configuration_disabled_doesNotCreateScheduler() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.notification.sqs.reminder-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ReminderScheduler.class);
                    assertThat(context).doesNotHaveBean("notificationReminderTaskScheduler");
                });
    }

    @Test
    @DisplayName("리마인더 발행을 켜면 스케줄러와 리마인더 전용 단일 스레드를 만든다")
    void configuration_enabled_createsDedicatedScheduler() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.notification.sqs.reminder-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ReminderScheduler.class);
                    ThreadPoolTaskScheduler scheduler = context.getBean(
                            "notificationReminderTaskScheduler", ThreadPoolTaskScheduler.class);
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                            .isEqualTo(1);
                    assertThat(scheduler.getThreadNamePrefix()).isEqualTo("notification-reminder-");
                });
    }
}
