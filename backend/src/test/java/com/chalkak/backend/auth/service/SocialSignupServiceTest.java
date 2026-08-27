package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

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
        UUID userId = socialSignupService.signup(SIGNUP_TOKEN);
        entityManager.flush();
        entityManager.clear();

        // Then
        User user = userRepository.findById(userId).orElseThrow();
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT)
                .orElseThrow();
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getSignatureOriginalStorageKey())
                .isEqualTo(storageKeys.originalStorageKey());
        assertThat(user.getSignatureThumbnailStorageKey())
                .isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(socialAccount.getUser().getId()).isEqualTo(userId);
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
                SUBJECT));
        entityManager.flush();
        entityManager.clear();

        // When
        UUID userId = socialSignupService.signup(SIGNUP_TOKEN);

        // Then
        assertThat(userId).isEqualTo(existingUser.getId());
    }

    @Test
    @DisplayName("탈퇴 회원에 연결된 소셜 계정은 회원가입을 재요청할 수 없다")
    void signup_withdrawnSocialAccount_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupTokenVerifier.verify(SIGNUP_TOKEN))
                .willReturn(verifiedSignupToken(uploadId, EMAIL));
        User withdrawnUser = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                withdrawnUser,
                SocialProvider.GOOGLE,
                SUBJECT));
        withdrawnUser.withdraw();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> socialSignupService.signup(SIGNUP_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("탈퇴한 회원은 회원가입할 수 없습니다.");
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
        UUID userId = socialSignupService.signup(SIGNUP_TOKEN);
        entityManager.flush();
        entityManager.clear();

        // Then
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getEmail()).isNull();
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
                SUBJECT));

        // When & Then
        assertThatThrownBy(() -> socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                ID_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 가입된 소셜 계정입니다.");
    }

    private VerifiedSocialIdentity identity() {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                SUBJECT,
                EMAIL);
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
