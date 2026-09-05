package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import com.chalkak.backend.user.service.UserWithdrawalService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ExistingSocialAccountLoginProcessorTest extends IntegrationTestSupport {

    private static final String SUBJECT = "social-subject";

    @Autowired
    private ExistingSocialAccountLoginProcessor existingSocialAccountLoginProcessor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("연결된 소셜 계정이 없으면 빈 값을 반환한다")
    void processIfExists_noSocialAccount_returnsEmpty() {
        // When
        Optional<SocialLoginSuccess> result =
                existingSocialAccountLoginProcessor.processIfExists(identity(SocialProvider.GOOGLE));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 일반 회원의 소셜 계정이 남아 있으면 빈 값을 반환한다")
    void processIfExists_withdrawnUser_returnsEmpty() {
        // Given
        User user = createUserWithSocialAccount(SocialProvider.GOOGLE);
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // When
        Optional<SocialLoginSuccess> result =
                existingSocialAccountLoginProcessor.processIfExists(identity(SocialProvider.GOOGLE));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 차단 회원의 소셜 계정이면 로그인을 거부한다")
    void processIfExists_withdrawnBannedUser_throwsForbiddenException() {
        // Given
        User user = createUserWithSocialAccount(SocialProvider.GOOGLE);
        user.ban();
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> existingSocialAccountLoginProcessor
                .processIfExists(identity(SocialProvider.GOOGLE)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("탈퇴한 차단 소셜 계정입니다.");
    }

    @Test
    @DisplayName("탈퇴하지 않은 차단 회원은 로그인에 성공한다")
    void processIfExists_bannedUser_returnsLoginSuccess() {
        // Given
        User user = UserFixture.create();
        user.ban();
        userRepository.save(user);
        saveSocialAccount(user, SocialProvider.GOOGLE);
        flushAndClear();

        // When
        Optional<SocialLoginSuccess> result =
                existingSocialAccountLoginProcessor.processIfExists(identity(SocialProvider.GOOGLE));

        // Then
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().userId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("정상 회원이면 회원 식별자를 담은 액세스 토큰과 리프레시 토큰을 발급한다")
    void processIfExists_activeUser_issuesAccessTokenAndRefreshToken() {
        // Given
        User user = createUserWithSocialAccount(SocialProvider.GOOGLE);
        flushAndClear();

        // When
        SocialLoginSuccess success = existingSocialAccountLoginProcessor
                .processIfExists(identity(SocialProvider.GOOGLE))
                .orElseThrow();

        // Then
        Jwt jwt = accessTokenProvider.jwtDecoder().decode(success.accessToken().value());
        assertThat(success.userId()).isEqualTo(user.getId());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(success.refreshToken().value()).isNotBlank();
        assertThat(storedRefreshTokenHashes(user)).hasSize(1);
    }

    @Test
    @DisplayName("같은 subject라도 다른 제공자에 연결된 계정은 찾지 않는다")
    void processIfExists_sameSubjectOtherProvider_returnsEmpty() {
        // Given
        createUserWithSocialAccount(SocialProvider.GOOGLE);
        flushAndClear();

        // When
        Optional<SocialLoginSuccess> result =
                existingSocialAccountLoginProcessor.processIfExists(identity(SocialProvider.APPLE));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("연결된 계정이 없으면 리프레시 토큰을 남기지 않는다")
    void processIfExists_noSocialAccount_doesNotIssueRefreshToken() {
        // Given
        User user = userRepository.save(UserFixture.create());
        flushAndClear();

        // When
        existingSocialAccountLoginProcessor.processIfExists(identity(SocialProvider.GOOGLE));

        // Then
        assertThat(storedRefreshTokenHashes(user)).isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 차단 회원의 로그인을 거부하면 리프레시 토큰을 남기지 않는다")
    void processIfExists_withdrawnBannedUser_doesNotIssueRefreshToken() {
        // Given
        User user = createUserWithSocialAccount(SocialProvider.GOOGLE);
        user.ban();
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> existingSocialAccountLoginProcessor
                .processIfExists(identity(SocialProvider.GOOGLE)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(storedRefreshTokenHashes(user)).isEmpty();
    }

    private User createUserWithSocialAccount(SocialProvider provider) {
        User user = userRepository.save(UserFixture.create());
        saveSocialAccount(user, provider);
        return user;
    }

    private void saveSocialAccount(User user, SocialProvider provider) {
        socialAccountRepository.save(SocialAccount.create(
                user,
                provider,
                fingerprintEncoder.encode(provider, SUBJECT)));
    }

    private List<String> storedRefreshTokenHashes(User user) {
        return jdbcTemplate.queryForList(
                "SELECT token_hash FROM user_refresh_tokens WHERE user_id = ?",
                String.class,
                user.getId());
    }

    private VerifiedSocialIdentity identity(SocialProvider provider) {
        return new VerifiedSocialIdentity(
                provider,
                SUBJECT,
                "user@chalkak.test");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
