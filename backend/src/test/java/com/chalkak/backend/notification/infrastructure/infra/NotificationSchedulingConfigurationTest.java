package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.notification.service.NotificationOutboxWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationSchedulingConfigurationTest {

    @Test
    @DisplayName("알림 전송을 끄면 Outbox 스케줄러를 등록하지 않는다")
    void create_deliveryDisabled_doesNotRegisterScheduler() {
        // Given
        ApplicationContextRunner contextRunner = createContextRunner(false);

        // When & Then
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(NotificationOutboxScheduler.class));
    }

    @Test
    @DisplayName("알림 전송을 켜면 Outbox 스케줄러를 등록한다")
    void create_deliveryEnabled_registersScheduler() {
        // Given
        ApplicationContextRunner contextRunner = createContextRunner(true);

        // When & Then
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(NotificationOutboxScheduler.class));
    }

    @Test
    @DisplayName("스케줄 실행은 Outbox 작업자에게 처리할 알림 묶음을 위임한다")
    void processDueNotifications_scheduled_delegatesToWorker() {
        // Given
        NotificationOutboxWorker worker = mock(NotificationOutboxWorker.class);
        NotificationOutboxScheduler scheduler = new NotificationOutboxScheduler(worker);

        // When
        scheduler.processDueNotifications();

        // Then
        then(worker).should().processDueBatch();
    }

    private ApplicationContextRunner createContextRunner(boolean deliveryEnabled) {
        return new ApplicationContextRunner()
                .withUserConfiguration(NotificationSchedulingConfiguration.class)
                .withBean(
                        NotificationOutboxWorker.class,
                        () -> mock(NotificationOutboxWorker.class)
                )
                .withPropertyValues(
                        "chalkak.admin.notification.delivery-enabled=" + deliveryEnabled,
                        "chalkak.admin.notification.poll-interval=1h"
                );
    }
}
