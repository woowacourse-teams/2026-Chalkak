package com.chalkak.backend.admin.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.auth.AdminRepository;
import com.chalkak.backend.auth.service.token.AccessTokenIssuer;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.domain.token.IssuedAccessToken;
import com.chalkak.backend.auth.domain.token.IssuedRefreshToken;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.time.Duration;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAuthenticationServiceTest {

    private static final String USERNAME = "auth-operator";
    private static final String RAW_PASSWORD = "test-password";
    private static final String FAILURE_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    @InjectMocks
    private AdminAuthenticationService service;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @Mock
    private AdminRefreshTokenService refreshTokens;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("비밀번호가 일치하면 관리자 scope의 발급 결과를 그대로 반환한다")
    void login_validCredentials_returnsIssuedTokens() {
        // Given
        Admin admin = persistAdmin(RAW_PASSWORD);
        givenIssuedTokens(admin);

        // When
        AdminLoginResult result = service.login(USERNAME, RAW_PASSWORD);

        // Then
        assertThat(result.adminId()).isEqualTo(admin.getId());
        assertThat(result.username()).isEqualTo(USERNAME);
        assertThat(result.accessToken().value()).isEqualTo("access-token");
        assertThat(result.refreshToken().value()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("잘못된 비밀번호는 공통 인증 실패 응답으로 거부한다")
    void login_wrongPassword_throwsCommonUnauthorized() {
        // Given
        persistAdmin(RAW_PASSWORD);

        // When & Then
        assertCommonAuthenticationFailure(() -> service.login(USERNAME, "wrong-password"));
    }

    @Test
    @DisplayName("없는 아이디도 BCrypt 비교를 수행하고 같은 인증 실패 응답으로 거부한다")
    void login_unknownUsername_hidesAccountExistence() {
        // When & Then
        assertCommonAuthenticationFailure(() -> service.login(USERNAME, RAW_PASSWORD));
        then(passwordEncoder).should().matches(
                RAW_PASSWORD, AdminAuthenticationService.DUMMY_PASSWORD_HASH);
    }

    @Test
    @DisplayName("인증된 관리자 식별자로 DB의 관리자 정보를 조회한다")
    void getCurrentAdmin_existingAdmin_returnsCurrentAdmin() {
        // Given
        Admin admin = createAdmin(RAW_PASSWORD);
        given(adminRepository.findById(admin.getId())).willReturn(Optional.of(admin));

        // When
        CurrentAdminResult result = service.getCurrentAdmin(admin.getId());

        // Then
        assertThat(result).isEqualTo(new CurrentAdminResult(admin.getId(), USERNAME));
    }

    @Test
    @DisplayName("존재하지 않는 관리자 식별자는 인증 실패로 거부한다")
    void getCurrentAdmin_missingAdmin_throwsUnauthorized() {
        // When & Then
        assertCommonAuthenticationFailure(() -> service.getCurrentAdmin(UUID.randomUUID()));
    }

    @ParameterizedTest
    @ValueSource(ints = {71, 72})
    @DisplayName("BCrypt의 바이트 길이 한도 직전과 경계 비밀번호로 로그인할 수 있다")
    void login_passwordWithinByteLimit_succeeds(int length) {
        // Given
        String password = "a".repeat(length);
        Admin admin = persistAdmin(password);
        givenIssuedTokens(admin);

        // When
        AdminLoginResult result = service.login(USERNAME, password);

        // Then
        assertThat(result.username()).isEqualTo(USERNAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "가"})
    @DisplayName("BCrypt의 72바이트를 넘는 로그인 비밀번호는 500 대신 인증 실패로 거부한다")
    void login_passwordExceedsByteLimit_throwsUnauthorized(String character) {
        // Given
        persistAdmin(RAW_PASSWORD);
        String password = character.repeat(73);

        // When & Then
        assertCommonAuthenticationFailure(() -> service.login(USERNAME, password));
    }

    private Admin persistAdmin(String password) {
        Admin admin = createAdmin(password);
        given(adminRepository.findByUsername(USERNAME)).willReturn(Optional.of(admin));
        return admin;
    }

    private Admin createAdmin(String password) {
        Admin admin = Admin.create(USERNAME, passwordEncoder.encode(password));
        ReflectionTestUtils.setField(
                admin,
                "id",
                UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657ac1")
        );
        return admin;
    }

    private void givenIssuedTokens(Admin admin) {
        given(accessTokenIssuer.issue(admin.getId(), AccessTokenScope.ADMIN))
                .willReturn(new IssuedAccessToken("access-token", Duration.ofMinutes(30)));
        given(refreshTokens.issue(admin)).willReturn(new IssuedRefreshToken(
                "refresh-token",
                Duration.ofDays(30),
                UUID.randomUUID()
        ));
    }

    private void assertCommonAuthenticationFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(
                        exception -> assertThat(((UnauthorizedException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessage(FAILURE_MESSAGE);
        verifyNoInteractions(accessTokenIssuer, refreshTokens);
    }
}
