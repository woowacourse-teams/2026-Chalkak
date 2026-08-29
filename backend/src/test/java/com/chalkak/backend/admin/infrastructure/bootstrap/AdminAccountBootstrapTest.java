package com.chalkak.backend.admin.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

class AdminAccountBootstrapTest {

    private static final String USERNAME = "operator";
    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Test
    @DisplayName("설정한 관리자 계정이 없으면 BCrypt 해시만 저장한다")
    void run_missingConfiguredAdmin_createsHashedAccount() {
        // Given
        AdminRepository repository = mock(AdminRepository.class);
        given(repository.findByUsername(USERNAME)).willReturn(Optional.empty());
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
                repository,
                new AdminAccountProperties(USERNAME, PASSWORD_HASH)
        );

        // When
        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        // Then
        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        then(repository).should().save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo(PASSWORD_HASH);
    }

    @Test
    @DisplayName("설정한 관리자 계정이 이미 있으면 비밀번호 해시를 덮어쓰지 않는다")
    void run_existingConfiguredAdmin_keepsStoredAccount() {
        // Given
        AdminRepository repository = mock(AdminRepository.class);
        given(repository.findByUsername(USERNAME))
                .willReturn(Optional.of(mock(Admin.class)));
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
                repository,
                new AdminAccountProperties(USERNAME, PASSWORD_HASH)
        );

        // When
        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        // Then
        then(repository).should().findByUsername(USERNAME);
        then(repository).shouldHaveNoMoreInteractions();
    }
}
