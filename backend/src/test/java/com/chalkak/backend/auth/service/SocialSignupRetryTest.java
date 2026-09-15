package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 가입 완료가 실패하면 소진 기록도 함께 롤백되는지는 서비스 트랜잭션이 실제로 끝나야 검증되므로 {@code @Transactional}
 * 격리를 쓰지 않고 직접 정리한다. Bean 교체 구성은 {@link SocialSignupServiceTest}와 같게 두어
 * Context를 함께 쓴다.
 */
class SocialSignupRetryTest extends IntegrationTestSupport {

    private static final String SIGNUP_TOKEN = "social-signup-retry-token";
    private static final String TOKEN_ID = "0198fd30-0000-7000-8000-00000000413b";

    @Autowired
    private SocialSignupService socialSignupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AppleAuthorizationCipher authorizationCipher;

    @MockitoBean
    private AppleTokenClient appleTokenClient;

    @MockitoSpyBean(name = "googleIdTokenVerifier")
    private IdTokenVerifier googleIdTokenVerifier;

    @MockitoBean
    private SignatureImageUploadIssuer signatureImageUploadIssuer;

    @MockitoBean
    private SocialSignupTokenIssuer socialSignupTokenIssuer;

    @MockitoBean
    private SocialSignupTokenVerifier socialSignupTokenVerifier;

    @MockitoBean
    private SignatureImageStorage signatureImageStorage;

    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("사인 이미지 처리 중으로 가입 완료가 실패하면 같은 회원가입 토큰으로 다시 시도해 가입할 수 있다")
    void signup_retryAfterProcessingPending_completesWithSameToken() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(new VerifiedSocialSignupToken(
                        SocialProvider.GOOGLE,
                        "google-retry-subject",
                        uploadId,
                        "retry@chalkak.test",
                        TOKEN_ID,
                        Instant.now().plus(Duration.ofMinutes(5))));
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(new SignatureStorageKeys(
                        "signatures/original/" + uploadId + ".png",
                        "signatures/thumbnail/" + uploadId + ".png"));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(false, true);
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        ErrorCode.SIGNATURE_PROCESSING_PENDING);
        assertThat(countConsumedTokens()).isZero();

        // When
        UUID userId = socialSignupService.signup(SIGNUP_TOKEN).userId();

        // Then
        assertThat(userId).isNotNull();
        assertThat(countConsumedTokens()).isEqualTo(1);
    }

    private int countConsumedTokens() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consumed_signup_tokens WHERE jti = ?",
                Integer.class,
                TOKEN_ID);
    }

    private void cleanUp() {
        databaseCleaner.clean();
        jdbcTemplate.update("DELETE FROM consumed_signup_tokens WHERE jti = ?", TOKEN_ID);
    }
}
