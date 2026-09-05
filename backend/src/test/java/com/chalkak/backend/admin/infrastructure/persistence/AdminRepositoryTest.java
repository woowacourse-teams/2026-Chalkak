package com.chalkak.backend.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminRepositoryImpl.class)
class AdminRepositoryTest {

    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("관리자를 저장하고 사용자명으로 조회한다")
    void findByUsername_savedAdmin_returnsAdmin() {
        // Given
        String username = "repository-admin-" + UUID.randomUUID();
        Admin savedAdmin = adminRepository.save(Admin.create(username, PASSWORD_HASH));
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Admin> result = adminRepository.findByUsername(username);

        // Then
        assertThat(result).isPresent();
        Admin admin = result.orElseThrow();
        assertThat(admin.getId()).isEqualTo(savedAdmin.getId());
        assertThat(admin.getId().version()).isEqualTo(7);
        assertThat(admin.getUsername()).isEqualTo(username);
        assertThat(admin.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(admin.getCreatedAt()).isNotNull();
        assertThat(admin.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("동일한 사용자명을 가진 관리자를 중복 저장할 수 없다")
    void save_duplicateUsername_throwsException() {
        // Given
        String username = "duplicate-admin-" + UUID.randomUUID();
        adminRepository.save(Admin.create(username, PASSWORD_HASH));
        entityManager.flush();

        // When & Then
        assertThatThrownBy(() -> adminRepository.save(Admin.create(username, PASSWORD_HASH)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }
}
