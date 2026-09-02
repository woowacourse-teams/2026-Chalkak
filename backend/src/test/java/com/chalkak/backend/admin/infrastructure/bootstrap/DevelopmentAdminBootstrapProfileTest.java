package com.chalkak.backend.admin.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.admin.repository.AdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DevelopmentAdminBootstrapProfileTest {

    @Test
    @DisplayName("운영 프로필에서는 개발 관리자 부트스트랩을 등록하지 않는다")
    void register_prodProfile_doesNotRegisterDevelopmentAdminBootstrap() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // Given
            context.getEnvironment().setActiveProfiles("prod");
            context.register(DevelopmentAdminBootstrap.class);

            // When
            context.refresh();

            // Then
            assertThat(context.getBeansOfType(DevelopmentAdminBootstrap.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("운영 프로필이 개발 프로필과 함께 활성화되어도 부트스트랩을 등록하지 않는다")
    void register_prodAndDevProfiles_doesNotRegisterDevelopmentAdminBootstrap() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // Given
            context.getEnvironment().setActiveProfiles("prod", "dev");
            context.registerBean(AdminRepository.class, () -> mock(AdminRepository.class));
            context.register(DevelopmentAdminBootstrap.class);

            // When
            context.refresh();

            // Then
            assertThat(context.getBeansOfType(DevelopmentAdminBootstrap.class)).isEmpty();
        }
    }
}
