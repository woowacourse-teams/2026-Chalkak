package com.chalkak.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.admin.infrastructure.bootstrap.AdminAccountBootstrap;
import com.chalkak.backend.admin.infrastructure.bootstrap.AdminAccountProperties;
import com.chalkak.backend.admin.infrastructure.bootstrap.DevelopmentAdminBootstrap;
import com.chalkak.backend.admin.infrastructure.infra.DevAdminActorResolver;
import com.chalkak.backend.admin.infrastructure.infra.SecurityContextAdminActorResolver;
import com.chalkak.backend.admin.repository.AdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminLocalConfigurationTest {

    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Test
    @DisplayName("로컬 환경변수로 인증 우회를 끄면 지정한 계정 생성기와 실제 관리자 Resolver를 사용한다")
    void localConfiguration_bypassDisabled_usesConfiguredAccountAndAuthentication() {
        // Given
        ApplicationContextRunner contextRunner = createContextRunner()
                .withPropertyValues(
                        "ADMIN_DEVELOPMENT_BYPASS_ENABLED=false",
                        "ADMIN_USERNAME=local-operator",
                        "ADMIN_PASSWORD_HASH=" + PASSWORD_HASH
                );

        // When & Then
        contextRunner.run(context -> {
            assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(AdminAccountBootstrap.class)
                    .hasSingleBean(SecurityContextAdminActorResolver.class)
                    .doesNotHaveBean(DevelopmentAdminBootstrap.class)
                    .doesNotHaveBean(DevAdminActorResolver.class);
            AdminAccountProperties properties = context.getBean(AdminAccountProperties.class);
            assertThat(properties.username()).isEqualTo("local-operator");
            assertThat(properties.passwordHash()).isEqualTo(PASSWORD_HASH);
        });
    }

    @Test
    @DisplayName("로컬 기본 설정은 실제 계정 없이 기존 개발 관리자 우회를 유지한다")
    void localConfiguration_defaultSettings_usesDevelopmentIdentity() {
        // Given
        ApplicationContextRunner contextRunner = createContextRunner();

        // When & Then
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(DevelopmentAdminBootstrap.class)
                .hasSingleBean(DevAdminActorResolver.class)
                .doesNotHaveBean(AdminAccountBootstrap.class)
                .doesNotHaveBean(SecurityContextAdminActorResolver.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain-test-password", "REPLACE_WITH_BCRYPT_HASH"})
    @DisplayName("로컬 실제 인증을 켜면 비어 있거나 평문 또는 예시인 비밀번호 해시를 거부한다")
    void localConfiguration_invalidPasswordHash_rejectsStartup(String passwordHash) {
        // Given
        ApplicationContextRunner contextRunner = createContextRunner()
                .withPropertyValues(
                        "ADMIN_DEVELOPMENT_BYPASS_ENABLED=false",
                        "ADMIN_USERNAME=local-operator",
                        "ADMIN_PASSWORD_HASH=" + passwordHash
                );

        // When & Then
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    @DisplayName("dev와 prod에서는 로컬 전용 환경변수로 인증 우회를 켤 수 없다")
    void deploymentConfiguration_localBypassVariable_keepsRealAuthentication(String profile) {
        // Given
        ApplicationContextRunner contextRunner = createContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=" + profile,
                        "ADMIN_DEVELOPMENT_BYPASS_ENABLED=true",
                        "ADMIN_USERNAME=deployment-operator",
                        "ADMIN_PASSWORD_HASH=" + PASSWORD_HASH
                );

        // When & Then
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(AdminAccountBootstrap.class)
                .hasSingleBean(SecurityContextAdminActorResolver.class)
                .doesNotHaveBean(DevelopmentAdminBootstrap.class)
                .doesNotHaveBean(DevAdminActorResolver.class));
    }

    private ApplicationContextRunner createContextRunner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(
                        AdminAccountBootstrap.class,
                        DevelopmentAdminBootstrap.class,
                        DevAdminActorResolver.class,
                        SecurityContextAdminActorResolver.class
                )
                .withBean(AdminRepository.class, () -> mock(AdminRepository.class))
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "spring.config.import="
                );
    }
}
