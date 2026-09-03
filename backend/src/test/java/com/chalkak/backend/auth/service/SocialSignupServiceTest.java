package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.AppleSignupAuthorization;
import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import com.chalkak.backend.user.repository.UserRepository;
import com.chalkak.backend.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SocialSignupServiceTest extends IntegrationTestSupport {

    private static final String ID_TOKEN = "google-id-token";
    private static final String SIGNUP_TOKEN = "social-signup-token";
    private static final String SUBJECT = "google-subject";
    private static final String EMAIL = "user@chalkak.test";

    @Autowired
    private SocialSignupService socialSignupService;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private AppleAuthorizationRepository appleAuthorizationRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

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

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("처리 완료된 서명 이미지로 소셜 회원가입하면 회원과 소셜 계정을 저장한다")
    void signup_completedSignature_savesUserAndSocialAccount() {
        // Given
        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = new SignatureStorageKeys(
                "signatures/original/" + uploadId + ".png",
                "signatures/thumbnail/" + uploadId + ".png");
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(true);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.toStorageKeys(uploadId)).willReturn(storageKeys);

        // When
        UUID userId = socialSignupService.signup(SIGNUP_TOKEN).userId();
        entityManager.flush();
        entityManager.clear();

        // Then
        User user = userRepository.findById(userId).orElseThrow();
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        SocialProvider.GOOGLE,
                        subjectHmac())
                .orElseThrow();
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getSignatureOriginalStorageKey())
                .isEqualTo(storageKeys.originalStorageKey());
        assertThat(user.getSignatureThumbnailStorageKey())
                .isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(socialAccount.getUser().getId()).isEqualTo(userId);
        assertThat(socialAccount.getSubjectHmac()).isEqualTo(subjectHmac());
        List<String> storedHashes = jdbcTemplate.queryForList(
                "SELECT token_hash FROM user_refresh_tokens WHERE user_id = ?",
                String.class,
                userId);
        assertThat(storedHashes).hasSize(1);
        assertThat(storedHashes.getFirst()).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("Apple 회원가입을 완료하면 암호화된 RT를 정식 인증 정보로 저장한다")
    void signup_appleSignupToken_savesAppleAuthorization() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(new VerifiedSocialSignupToken(
                        SocialProvider.APPLE,
                        "apple-subject",
                        uploadId,
                        "user@privaterelay.appleid.com",
                        new AppleSignupAuthorization(
                                "com.chalkak.ios",
                                "encrypted-apple-refresh-token")));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(true);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));

        // When
        socialSignupService.signup(SIGNUP_TOKEN);
        entityManager.flush();
        entityManager.clear();

        // Then
        String appleSubjectHmac = fingerprintEncoder.encode(
                SocialProvider.APPLE,
                "apple-subject");
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        SocialProvider.APPLE,
                        appleSubjectHmac)
                .orElseThrow();
        AppleAuthorization authorization = appleAuthorizationRepository
                .findAllBySocialAccountId(socialAccount.getId())
                .getFirst();
        assertThat(authorization.getClientId()).isEqualTo("com.chalkak.ios");
        assertThat(authorization.getEncryptedRefreshToken())
                .isEqualTo("encrypted-apple-refresh-token");
    }

    @Test
    @DisplayName("서명 이미지가 처리 중이면 소셜 회원가입을 완료하지 않는다")
    void signup_processingSignature_throwsProcessingPendingError() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        ErrorCode.SIGNATURE_PROCESSING_PENDING)
                .hasMessage("사인 이미지 처리 중입니다.");
    }

    @Test
    @DisplayName("이미 가입된 활성 소셜 계정이 회원가입을 재요청하면 기존 회원 식별자를 반환한다")
    void signup_existingActiveSocialAccount_returnsExistingUserId() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        User existingUser = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                existingUser,
                SocialProvider.GOOGLE,
                subjectHmac()));
        entityManager.flush();
        entityManager.clear();

        // When
        UUID userId = socialSignupService.signup(SIGNUP_TOKEN).userId();

        // Then
        assertThat(userId).isEqualTo(existingUser.getId());
    }

    @Test
    @DisplayName("탈퇴 후 동일한 소셜 계정으로 회원가입하면 새로운 회원을 생성한다")
    void signup_withdrawnSocialAccount_createsNewUser() {
        // Given
        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        given(signatureImageStorage.toStorageKeys(uploadId)).willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(true);
        User withdrawnUser = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                withdrawnUser,
                SocialProvider.GOOGLE,
                subjectHmac()));
        UUID withdrawnUserId = withdrawnUser.getId();
        userService.withdraw(withdrawnUserId);
        entityManager.flush();
        entityManager.clear();

        // When
        UUID newUserId = socialSignupService.signup(SIGNUP_TOKEN).userId();
        entityManager.flush();
        entityManager.clear();

        // Then
        User oldUser = userRepository.findById(withdrawnUserId).orElseThrow();
        User newUser = userRepository.findById(newUserId).orElseThrow();
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        SocialProvider.GOOGLE,
                        subjectHmac())
                .orElseThrow();

        assertThat(newUserId).isNotEqualTo(withdrawnUserId);
        assertThat(oldUser.isDeleted()).isTrue();
        assertThat(oldUser.getEmail())
                .isEqualTo("withdrawn+" + withdrawnUserId + "@chalkak.invalid");
        assertThat(oldUser.getSignatureOriginalStorageKey())
                .isEqualTo("withdrawn/" + withdrawnUserId);
        assertThat(newUser.getEmail()).isEqualTo(EMAIL);
        assertThat(socialAccount.getUser().getId()).isEqualTo(newUserId);
    }

    @Test
    @DisplayName("차단 회원에 연결된 소셜 계정은 회원가입 재요청으로 우회할 수 없다")
    void signup_bannedSocialAccount_throwsForbiddenException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        User bannedUser = UserFixture.create();
        bannedUser.ban();
        userRepository.save(bannedUser);
        socialAccountRepository.save(SocialAccount.create(
                bannedUser,
                SocialProvider.GOOGLE,
                subjectHmac()));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("차단 회원이 탈퇴한 뒤에는 기존 회원가입 토큰으로 재가입할 수 없다")
    void signup_withdrawnBannedUser_throwsForbiddenException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(true);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        user.ban();
        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("차단된 소셜 계정입니다.");
        verifyNoInteractions(signatureImageStorage);
    }

    @Test
    @DisplayName("탈퇴한 일반 회원의 소셜 계정 행이 남아 있으면 회원가입을 완료하지 않는다")
    void signup_withdrawnUserWithSocialAccount_throwsBusinessException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        ErrorCode.BUSINESS_ERROR)
                .hasMessage("이미 가입된 소셜 계정입니다.");
        verifyNoInteractions(signatureImageStorage);
    }

    @Test
    @DisplayName("다른 회원이 사용한 서명 업로드 ID로는 회원가입할 수 없다")
    void signup_usedSignatureUploadId_throwsNotFoundException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = new SignatureStorageKeys(
                "signatures/original/" + uploadId + ".png",
                "signatures/thumbnail/" + uploadId + ".png");
        userRepository.save(User.create("existing@chalkak.test", storageKeys));
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(true);
        given(signatureImageStorage.toStorageKeys(uploadId)).willReturn(storageKeys);

        // When & Then
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("업로드한 사인 이미지를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("업로드한 서명 이미지를 찾을 수 없으면 회원가입을 완료하지 않는다")
    void signup_missingSignatureImage_throwsNotFoundException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("업로드한 사인 이미지를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("사용할 수 없는 서명 이미지로는 회원가입을 완료하지 않는다")
    void signup_invalidSignatureImage_throwsBusinessException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/jpeg", 1024L)));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용할 수 없는 사인 이미지입니다.");
    }

    @Test
    @DisplayName("소셜 제공자가 이메일을 제공하지 않아도 회원가입할 수 있다")
    void signup_nullEmail_savesUserWithoutEmail() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, null));
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(true);

        // When
        UUID userId = socialSignupService.signup(SIGNUP_TOKEN).userId();
        entityManager.flush();
        entityManager.clear();

        // Then
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getEmail()).isNull();
    }

    @Test
    @DisplayName("소셜 회원가입을 완료하면 새 회원 식별자를 담은 액세스 토큰을 발급한다")
    void signup_completedSignature_issuesAccessToken() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/png", 1024L)));
        given(signatureImageStorage.isProcessingCompleted(uploadId)).willReturn(true);

        // When
        SocialSignupResult result = socialSignupService.signup(SIGNUP_TOKEN);

        // Then
        Jwt jwt = accessTokenProvider.jwtDecoder()
                .decode(result.accessToken().value());
        assertThat(jwt.getSubject()).isEqualTo(result.userId().toString());
        assertThat(result.accessToken().expiresIn()).isPositive();
    }

    @Test
    @DisplayName("신규 소셜 계정이 요청하면 서명 이미지 업로드 URL을 발급한다")
    void createSignatureUpload_newSocialAccount_issuesUploadUrl() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);
        given(signatureImageUploadIssuer.issue(any(UUID.class)))
                .willAnswer(invocation -> new SignatureImageUpload(
                        invocation.getArgument(0),
                        "https://s3.example.com/presigned",
                        300L));
        given(socialSignupTokenIssuer.issue(any(), any(UUID.class)))
                .willReturn(new IssuedSocialSignupToken("social-signup-token"));

        // When
        SocialSignupSignatureUploadResult result =
                socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                ID_TOKEN);

        // Then
        assertThat(result.upload().uploadId()).isNotNull();
        assertThat(result.upload().uploadUrl())
                .isEqualTo("https://s3.example.com/presigned");
        assertThat(result.upload().expiresInSeconds()).isEqualTo(300L);
        assertThat(result.signupToken().value())
                .isEqualTo("social-signup-token");
        verify(socialSignupTokenIssuer).issue(
                identity(),
                result.upload().uploadId());
    }

    @Test
    @DisplayName("이미 가입된 소셜 계정은 회원가입용 서명 업로드 URL을 발급받을 수 없다")
    void createSignatureUpload_existingSocialAccount_throwsBusinessException() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));

        // When & Then
        assertThatThrownBy(() -> socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                ID_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 가입된 소셜 계정입니다.");
    }

    @Test
    @DisplayName("탈퇴했지만 소셜 계정 행이 남아 있으면 회원가입용 서명 업로드 URL을 발급하지 않는다")
    void createSignatureUpload_withdrawnUserWithSocialAccount_throwsBusinessException() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                ID_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_ERROR)
                .hasMessage("이미 가입된 소셜 계정입니다.");
        verifyNoInteractions(signatureImageUploadIssuer, socialSignupTokenIssuer);
    }

    @Test
    @DisplayName("차단 회원은 회원가입용 사인 업로드 발급으로 우회할 수 없다")
    void createSignatureUpload_bannedSocialAccount_throwsForbiddenException() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);
        User bannedUser = UserFixture.create();
        bannedUser.ban();
        userRepository.save(bannedUser);
        socialAccountRepository.save(SocialAccount.create(
                bannedUser,
                SocialProvider.GOOGLE,
                subjectHmac()));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                ID_TOKEN))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("차단 회원이 탈퇴한 뒤에는 회원가입용 서명 업로드 URL을 발급하지 않는다")
    void createSignatureUpload_withdrawnBannedUser_throwsForbiddenException() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        user.ban();
        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                ID_TOKEN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("차단된 소셜 계정입니다.");
        verifyNoInteractions(signatureImageUploadIssuer, socialSignupTokenIssuer);
    }

    private VerifiedSocialIdentity identity() {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                SUBJECT,
                EMAIL);
    }

    private String subjectHmac() {
        return fingerprintEncoder.encode(SocialProvider.GOOGLE, SUBJECT);
    }

    private VerifiedSocialSignupToken verifiedSignupToken(
            UUID uploadId,
            String email
    ) {
        return new VerifiedSocialSignupToken(
                SocialProvider.GOOGLE,
                SUBJECT,
                uploadId,
                email);
    }

    private SignatureStorageKeys storageKeys(UUID uploadId) {
        return new SignatureStorageKeys(
                "signatures/original/" + uploadId + ".png",
                "signatures/thumbnail/" + uploadId + ".png");
    }
}
