package com.chalkak.backend.admin.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminAccountBootstrapTest extends IntegrationTestSupport {

    private static final String USERNAME = "operator";
    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("설정한 관리자 계정이 없으면 BCrypt 해시만 저장한다")
    void run_missingConfiguredAdmin_createsHashedAccount() {
        // Given
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
                adminRepository,
                new AdminAccountProperties(USERNAME, PASSWORD_HASH)
        );

        // When
        bootstrap.run(new DefaultApplicationArguments(new String[0]));
        entityManager.flush();
        entityManager.clear();

        // Then
        Admin savedAdmin = adminRepository.findByUsername(USERNAME).orElseThrow();
        assertThat(savedAdmin.getUsername()).isEqualTo(USERNAME);
        assertThat(savedAdmin.getPasswordHash()).isEqualTo(PASSWORD_HASH);
    }

    @Test
    @DisplayName("설정한 관리자 계정이 이미 있으면 비밀번호 해시를 덮어쓰지 않는다")
    void run_existingConfiguredAdmin_keepsStoredAccount() {
        // Given
        String existingHash = passwordEncoder.encode("existing-test-password");
        Admin existingAdmin = adminRepository.save(Admin.create(USERNAME, existingHash));
        entityManager.flush();
        entityManager.clear();
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
                adminRepository,
                new AdminAccountProperties(USERNAME, PASSWORD_HASH)
        );

        // When
        bootstrap.run(new DefaultApplicationArguments(new String[0]));
        entityManager.flush();
        entityManager.clear();

        // Then
        Admin savedAdmin = adminRepository.findByUsername(USERNAME).orElseThrow();
        assertThat(savedAdmin.getId()).isEqualTo(existingAdmin.getId());
        assertThat(savedAdmin.getPasswordHash()).isEqualTo(existingHash);
    }
}
