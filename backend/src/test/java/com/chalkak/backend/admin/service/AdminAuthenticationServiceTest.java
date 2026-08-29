package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.service.AccessTokenIssuer;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAuthenticationServiceTest {

    private static final String USERNAME = "operator";
    private static final String RAW_PASSWORD = "safe-password";
    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String FAILURE_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private AdminRepository adminRepository;
    private PasswordEncoder passwordEncoder;
    private AccessTokenIssuer accessTokenIssuer;
    private AdminAuthenticationService service;

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        accessTokenIssuer = mock(AccessTokenIssuer.class);
        service = new AdminAuthenticationService(
                adminRepository,
                passwordEncoder,
                accessTokenIssuer
        );
    }

    @Test
    @DisplayName("아이디와 비밀번호가 일치하면 관리자 액세스 토큰을 발급한다")
    void login_validCredentials_returnsAdminAccessToken() {
        // Given
        UUID adminId = UUID.randomUUID();
        Admin admin = mock(Admin.class);
        IssuedAccessToken token = new IssuedAccessToken("admin-token", Duration.ofHours(1));
        given(admin.getId()).willReturn(adminId);
        given(admin.getUsername()).willReturn(USERNAME);
        given(admin.getPasswordHash()).willReturn(PASSWORD_HASH);
        given(adminRepository.findByUsername(USERNAME)).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(accessTokenIssuer.issue(adminId, AccessTokenScope.ADMIN)).willReturn(token);

        // When
        AdminLoginResult result = service.login(USERNAME, RAW_PASSWORD);

        // Then
        assertThat(result.adminId()).isEqualTo(adminId);
        assertThat(result.username()).isEqualTo(USERNAME);
        assertThat(result.accessToken()).isEqualTo(token);
    }

    @Test
    @DisplayName("비밀번호가 다르면 공통 인증 실패 응답을 반환한다")
    void login_wrongPassword_throwsCommonUnauthorized() {
        // Given
        Admin admin = mock(Admin.class);
        given(admin.getPasswordHash()).willReturn(PASSWORD_HASH);
        given(adminRepository.findByUsername(USERNAME)).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(false);

        // When & Then
        assertCommonAuthenticationFailure(() -> service.login(USERNAME, RAW_PASSWORD));
        then(accessTokenIssuer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 아이디도 비밀번호 검증을 수행한 뒤 같은 인증 실패 응답을 반환한다")
    void login_unknownUsername_hidesAccountExistence() {
        // Given
        given(adminRepository.findByUsername(USERNAME)).willReturn(Optional.empty());
        given(passwordEncoder.matches(eq(RAW_PASSWORD), anyString())).willReturn(false);

        // When & Then
        assertCommonAuthenticationFailure(() -> service.login(USERNAME, RAW_PASSWORD));
        then(passwordEncoder).should().matches(RAW_PASSWORD, PASSWORD_HASH);
        then(accessTokenIssuer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증된 관리자 식별자로 현재 관리자 정보를 조회한다")
    void getCurrentAdmin_existingAdmin_returnsCurrentAdmin() {
        // Given
        UUID adminId = UUID.randomUUID();
        Admin admin = mock(Admin.class);
        given(admin.getId()).willReturn(adminId);
        given(admin.getUsername()).willReturn(USERNAME);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(admin));

        // When
        CurrentAdminResult result = service.getCurrentAdmin(adminId);

        // Then
        assertThat(result).isEqualTo(new CurrentAdminResult(adminId, USERNAME));
    }

    private void assertCommonAuthenticationFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(exception -> assertThat(
                        ((UnauthorizedException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessage(FAILURE_MESSAGE);
    }
}
