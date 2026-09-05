package com.chalkak.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.admin.infrastructure.bootstrap.DevelopmentAdminBootstrap;
import com.chalkak.backend.admin.infrastructure.infra.DevAdminActorResolver;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import tools.jackson.databind.ObjectMapper;

class AdminDevelopmentBypassTest {

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod", "dev,test", "prod,local", "prod,dev", "unknown"})
    @DisplayName("배포 환경과 알 수 없는 프로필에서는 관리자 인증 우회 설정을 거부한다")
    void securityFilterChain_bypassOutsideLocalTest_rejectsStartup(String profiles) {
        // Given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(SecurityConfig.class)
                .withBean(HttpSecurity.class, () -> mock(HttpSecurity.class, RETURNS_SELF))
                .withBean(JwtAccessTokenProvider.class, () -> mock(JwtAccessTokenProvider.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "spring.profiles.active=" + profiles,
                        "chalkak.admin.authentication.development-bypass-enabled=true"
                );

        // When & Then
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("개발 관리자 인증 우회는 local/test에서만 사용할 수 있습니다.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod", "dev,test", "prod,local", "prod,dev"})
    @DisplayName("dev 또는 prod가 포함되면 개발 관리자 생성기와 Resolver를 등록하지 않는다")
    void developmentAdmin_deploymentProfiles_doesNotRegisterBypass(String profiles) {
        // Given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(DevelopmentAdminBootstrap.class, DevAdminActorResolver.class)
                .withBean(AdminRepository.class, () -> mock(AdminRepository.class))
                .withPropertyValues(
                        "spring.profiles.active=" + profiles,
                        "chalkak.admin.authentication.development-bypass-enabled=true"
                );

        // When & Then
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(DevelopmentAdminBootstrap.class)
                .doesNotHaveBean(DevAdminActorResolver.class));
    }
}
