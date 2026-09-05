package com.chalkak.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.admin.repository.AdminRefreshTokenRepository;
import com.chalkak.backend.auth.infrastructure.infra.refresh.RefreshTokenCleanupScheduler;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 스케줄링 전체를 끄는 키와 정리 작업 하나를 끄는 키가 서로 독립인지 확인한다. 둘이 섞이면 주기
 * 작업이 하나 더 늘었을 때 그 작업과 무관한 키가 새 작업까지 조용히 끄고, 증상이 원인에서 멀어진다.
 */
class SchedulingConfigurationTest {

    @Test
    @DisplayName("기본 설정은 스케줄링과 리프레시 토큰 정리 작업을 모두 켠다")
    void schedulingConfiguration_defaultSettings_enablesBoth() {
        // given
        ApplicationContextRunner contextRunner = createContextRunner();

        // when & then
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(SchedulingConfig.class)
                .hasSingleBean(RefreshTokenCleanupScheduler.class));
    }

    @Test
    @DisplayName("스케줄링을 끄면 정리 작업 빈은 남고 스케줄링만 꺼진다")
    void schedulingConfiguration_schedulingDisabled_keepsCleanupBean() {
        // given
        ApplicationContextRunner contextRunner = createContextRunner()
                .withPropertyValues("chalkak.scheduling.enabled=false");

        // when & then
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(SchedulingConfig.class)
                .hasSingleBean(RefreshTokenCleanupScheduler.class));
    }

    @Test
    @DisplayName("리프레시 토큰 정리 작업만 끄면 스케줄링 자체는 켜진 채로 남는다")
    void schedulingConfiguration_cleanupDisabled_keepsSchedulingEnabled() {
        // given
        ApplicationContextRunner contextRunner = createContextRunner()
                .withPropertyValues("chalkak.auth.refresh-token.cleanup.enabled=false");

        // when & then
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(SchedulingConfig.class)
                .doesNotHaveBean(RefreshTokenCleanupScheduler.class));
    }

    private ApplicationContextRunner createContextRunner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(SchedulingConfig.class, RefreshTokenCleanupScheduler.class)
                .withBean(UserRefreshTokenRepository.class,
                        () -> mock(UserRefreshTokenRepository.class))
                .withBean(AdminRefreshTokenRepository.class,
                        () -> mock(AdminRefreshTokenRepository.class))
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues(
                        // test 프로필은 application-test.yml이 스케줄링을 꺼 두므로, 운영과 같은
                        // 기본값에서 두 키의 관계를 보려면 다른 프로필로 띄운다.
                        "spring.profiles.active=local",
                        "spring.config.import="
                );
    }
}
