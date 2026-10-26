package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.config.SchedulingConfig;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class PushDeliveryConfigurationTest {
    @Test
    @DisplayName("Lease 갱신 실행기는 공용 스케줄러를 대체하지 않고 컨텍스트 종료 때 종료한다")
    void configuration_dedicatedExecutor_preservesCommonSchedulerAndCloses() {
        // Given
        AtomicReference<ScheduledExecutorService> executor = new AtomicReference<>();

        // When
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SchedulingConfig.class, PushDeliveryConfiguration.class)
                .withPropertyValues("spring.task.scheduling.pool.size=4")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThreadPoolTaskScheduler common = context.getBean("taskScheduler",
                            ThreadPoolTaskScheduler.class);
                    executor.set(context.getBean("notificationLeaseRenewalExecutor",
                            ScheduledExecutorService.class));
                    assertThat(common.getScheduledThreadPoolExecutor().getCorePoolSize())
                            .isEqualTo(4);
                    assertThat(executor.get()).isNotSameAs(common.getScheduledExecutor());
                });

        // Then
        assertThat(executor.get().isShutdown()).isTrue();
    }
}
