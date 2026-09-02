package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminAuthenticationServiceTest extends IntegrationTestSupport {

    private static final String USERNAME = "auth-operator";
    private static final String RAW_PASSWORD = "test-password";
    private static final String FAILURE_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    @Autowired
    private AdminAuthenticationService service;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("실제 저장된 계정의 비밀번호가 일치하면 검증 가능한 관리자 JWT를 발급한다")
    void login_validCredentials_returnsAdminAccessToken() {
        // Given
        Admin admin = persistAdmin(RAW_PASSWORD);

        // When
        AdminLoginResult result = service.login(USERNAME, RAW_PASSWORD);
        Jwt jwt = accessTokenProvider.jwtDecoder().decode(result.accessToken().value());

        // Then
        assertThat(result.adminId()).isEqualTo(admin.getId());
        assertThat(result.username()).isEqualTo(USERNAME);
        assertThat(jwt.getSubject()).isEqualTo(admin.getId().toString());
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("ADMIN");
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
        Admin admin = persistAdmin(RAW_PASSWORD);

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
        persistAdmin(password);

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
        Admin admin = adminRepository.save(Admin.create(USERNAME, passwordEncoder.encode(password)));
        entityManager.flush();
        entityManager.clear();
        return admin;
    }

    private void assertCommonAuthenticationFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(exception -> assertThat(((UnauthorizedException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessage(FAILURE_MESSAGE);
    }
}
