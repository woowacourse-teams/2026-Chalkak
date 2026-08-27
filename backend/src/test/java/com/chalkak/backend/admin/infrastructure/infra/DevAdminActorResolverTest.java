package com.chalkak.backend.admin.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.infrastructure.bootstrap.DevelopmentAdminBootstrap;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DevAdminActorResolverTest extends IntegrationTestSupport {

    @Autowired
    private DevAdminActorResolver devAdminActorResolver;

    @Autowired
    private AdminRepository adminRepository;

    @Test
    @DisplayName("개발 환경에서는 저장된 dev-admin의 ID를 현재 작업자로 반환한다")
    void resolve_developmentProfile_returnsPersistedDevelopmentAdmin() {
        // Given
        Admin developmentAdmin = adminRepository.findByUsername(
                DevelopmentAdminBootstrap.DEVELOPMENT_ADMIN_USERNAME).orElseThrow();

        // When
        AuthenticatedAdmin authenticatedAdmin = devAdminActorResolver.resolve();

        // Then
        assertThat(authenticatedAdmin.adminId()).isEqualTo(developmentAdmin.getId());
    }
}
