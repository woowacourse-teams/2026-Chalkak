package com.chalkak.backend.admin.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DevelopmentAdminBootstrapTest extends IntegrationTestSupport {

    @Autowired
    private DevelopmentAdminBootstrap developmentAdminBootstrap;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM admins");
    }

    @Test
    @DisplayName("개발 관리자가 없으면 사용할 수 없는 임의 비밀번호의 BCrypt 해시로 생성한다")
    void run_missingDevelopmentAdmin_createsHashedAdmin() {
        // When
        developmentAdminBootstrap.run(new DefaultApplicationArguments(new String[0]));

        // Then
        Admin admin = adminRepository.findByUsername(
                DevelopmentAdminBootstrap.DEVELOPMENT_ADMIN_USERNAME).orElseThrow();
        assertThat(admin.getId()).isNotNull();
        assertThat(admin.getPasswordHash())
                .matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")
                .isNotEqualTo("dev-admin");
    }

    @Test
    @DisplayName("개발 부트스트랩을 반복 실행해도 관리자 한 명만 유지한다")
    void run_repeatedExecution_keepsSingleDevelopmentAdmin() {
        // Given
        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
        developmentAdminBootstrap.run(arguments);
        Admin firstAdmin = adminRepository.findByUsername(
                DevelopmentAdminBootstrap.DEVELOPMENT_ADMIN_USERNAME).orElseThrow();

        // When
        developmentAdminBootstrap.run(arguments);

        // Then
        Admin currentAdmin = adminRepository.findByUsername(
                DevelopmentAdminBootstrap.DEVELOPMENT_ADMIN_USERNAME).orElseThrow();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admins WHERE username = ?",
                Integer.class,
                DevelopmentAdminBootstrap.DEVELOPMENT_ADMIN_USERNAME);
        assertThat(count).isOne();
        assertThat(currentAdmin.getId()).isEqualTo(firstAdmin.getId());
        assertThat(currentAdmin.getPasswordHash()).isEqualTo(firstAdmin.getPasswordHash());
    }
}
