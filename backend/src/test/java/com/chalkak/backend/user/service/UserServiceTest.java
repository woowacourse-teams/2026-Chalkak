package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.RefreshTokenHasher;
import com.chalkak.backend.auth.service.AppleAuthorizationSnapshot;
import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureProcessingStatus;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.domain.UserStatus;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserServiceTest extends IntegrationTestSupport {

    private static final StoredImageMetadata VALID_IMAGE =
            new StoredImageMetadata("image/png", 1024L);

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private AppleAuthorizationRepository appleAuthorizationRepository;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SignatureImageStorage signatureImageStorage;

    @MockitoBean
    private SignatureImageUploadIssuer signatureImageUploadIssuer;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("탈퇴하면 개인 식별 정보가 비식별화되고 탈퇴 시각이 기록된다")
    void withdraw_activeUser_anonymizesPersonalData() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        // When
        userService.withdraw(id, List.of());
        flushAndClear();

        // Then
        User withdrawn = userRepository.findById(id).orElseThrow();

        assertThat(withdrawn.getEmail())
                .isEqualTo("withdrawn+" + id + "@chalkak.invalid");
        assertThat(withdrawn.getSignatureOriginalStorageKey())
                .isEqualTo("withdrawn/" + id);
        assertThat(withdrawn.getSignatureThumbnailStorageKey()).isNull();
        assertThat(withdrawn.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("탈퇴하면 연결된 소셜 계정이 삭제된다")
    void withdraw_activeUser_deletesSocialAccount() {
        // Given
        User user = userRepository.save(UserFixture.create());
        String subjectHmac = fingerprintEncoder.encode(
                SocialProvider.GOOGLE,
                "google-subject");
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac));
        flushAndClear();

        // When
        userService.withdraw(user.getId(), List.of());
        flushAndClear();

        // Then
        assertThat(socialAccountRepository.findByProviderAndSubjectHmac(
                SocialProvider.GOOGLE,
                subjectHmac))
                .isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 회원은 살아있는 회원 조회에서 제외된다")
    void withdraw_activeUser_excludesFromActiveQuery() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        // When
        userService.withdraw(id, List.of());
        flushAndClear();

        // Then
        assertThat(userRepository.findActiveById(id)).isEmpty();
        assertThat(userRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("탈퇴하면 모든 기기의 리프레시 토큰이 폐기되고 다른 회원의 토큰은 그대로 남는다")
    void withdraw_activeUser_revokesEveryRefreshTokenOfThatUser() {
        // Given
        User user = userRepository.save(UserFixture.create());
        User otherUser = userRepository.save(UserFixture.create());
        flushAndClear();
        IssuedRefreshToken firstDevice = userRefreshTokenService.issue(user);
        IssuedRefreshToken secondDevice = userRefreshTokenService.issue(user);
        IssuedRefreshToken otherUserDevice = userRefreshTokenService.issue(otherUser);
        flushAndClear();

        // When
        userService.withdraw(user.getId(), List.of());
        flushAndClear();

        // Then
        assertThat(countLiveRefreshTokens(user.getId())).isZero();
        assertThat(findRefreshTokenRevokedAt(firstDevice.value())).isNotNull();
        assertThat(findRefreshTokenRevokedAt(secondDevice.value())).isNotNull();
        assertThat(findRefreshTokenRevokedAt(otherUserDevice.value())).isNull();
    }

    @Test
    @DisplayName("탈퇴한 회원의 리프레시 토큰으로 재발급하면 재로그인을 요구한다")
    void withdraw_activeUser_blocksRefreshWithIssuedToken() {
        // Given
        User user = userRepository.save(UserFixture.create());
        flushAndClear();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();
        userService.withdraw(user.getId(), List.of());
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userRefreshTokenService.refresh(issued.value()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("다시 로그인해 주세요.")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REAUTHENTICATION_REQUIRED);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 탈퇴는 인증 실패로 거부한다")
    void withdraw_notExistingUser_throwsUnauthorizedException() {
        // Given
        UUID notExistingId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> userService.withdraw(notExistingId, List.of()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }

    @Test
    @DisplayName("이미 탈퇴한 회원은 존재하지 않는 회원과 동일하게 인증 실패로 거부한다")
    void withdraw_alreadyWithdrawnUser_throwsUnauthorizedException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        userService.withdraw(id, List.of());
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userService.withdraw(id, List.of()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }

    @Test
    @DisplayName("활성 회원이 사인 업로드를 요청하면 새 업로드 URL을 발급한다")
    void createSignatureUpload_activeUser_issuesUploadUrl() {
        // Given
        UUID userId = userRepository.save(UserFixture.create()).getId();
        flushAndClear();
        String uploadUrl = "https://s3.example.com/presigned";
        given(signatureImageUploadIssuer.issue(any(UUID.class)))
                .willAnswer(invocation -> {
                    UUID uploadId = invocation.getArgument(0);
                    return new SignatureImageUpload(uploadId, uploadUrl, 300L);
                });

        // When
        SignatureImageUpload upload = userService.createSignatureUpload(userId);

        // Then
        assertThat(upload.uploadId()).isNotNull();
        assertThat(upload.uploadUrl()).isEqualTo(uploadUrl);
        assertThat(upload.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 사인 업로드 URL 발급은 인증 실패로 거부한다")
    void createSignatureUpload_notExistingUser_throwsUnauthorizedException() {
        // Given
        UUID userId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> userService.createSignatureUpload(userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
        verifyNoInteractions(signatureImageUploadIssuer);
    }

    @Test
    @DisplayName("탈퇴한 회원의 사인 업로드 URL 발급은 인증 실패로 거부한다")
    void createSignatureUpload_withdrawnUser_throwsUnauthorizedException() {
        // Given
        UUID userId = userRepository.save(UserFixture.create()).getId();
        userService.withdraw(userId, List.of());
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userService.createSignatureUpload(userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
        verifyNoInteractions(signatureImageUploadIssuer);
    }

    @Test
    @DisplayName("처리 중인 새 사인이 없으면 현재 활성 사인의 원본과 썸네일 URL을 반환한다")
    void getSignature_withoutPending_returnsActiveSignatureUrls() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID userId = saved.getId();
        String originalStorageKey = saved.getSignatureOriginalStorageKey();
        String thumbnailStorageKey = saved.getSignatureThumbnailStorageKey();
        String originalImageUrl = "https://cdn.test.chalkak/" + originalStorageKey;
        String thumbnailImageUrl = "https://cdn.test.chalkak/" + thumbnailStorageKey;
        flushAndClear();

        given(signatureImageStorage.toImageUrl(originalStorageKey))
                .willReturn(originalImageUrl);
        given(signatureImageStorage.toImageUrl(thumbnailStorageKey))
                .willReturn(thumbnailImageUrl);

        // When
        UserSignatureResult result = userService.getSignature(userId);

        // Then
        assertThat(result.originalImageUrl()).isEqualTo(originalImageUrl);
        assertThat(result.thumbnailImageUrl()).isEqualTo(thumbnailImageUrl);
    }

    @Test
    @DisplayName("사인 처리가 제한 시간을 넘으면 상태 변경 없이 재등록 오류를 발생시킨다")
    void getSignature_timedOutPending_keepsProcessingAndThrowsBusinessException() {
        // Given
        User user = UserFixture.create();
        UUID uploadId = UUID.randomUUID();
        user.startSignatureProcessing(
                uploadId,
                Instant.now().minus(Duration.ofMinutes(8)));
        UUID userId = userRepository.save(user).getId();
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userService.getSignature(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사인 이미지 처리에 실패했습니다. 사인을 다시 등록해 주세요.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.SIGNATURE_REGISTRATION_REQUIRED));

        flushAndClear();
        User updated = userRepository.findById(userId).orElseThrow();

        assertThat(updated.getPendingSignatureUploadId()).isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
        assertThat(updated.getSignatureProcessingStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("사인 처리 시작 후 7분이 지나지 않았으면 현재 활성 사인의 URL들을 반환한다")
    void getSignature_processingWithinTimeout_returnsActiveSignatureUrls() {
        // Given
        User user = UserFixture.create();
        user.startSignatureProcessing(
                UUID.randomUUID(),
                Instant.now().minus(Duration.ofMinutes(6)));
        UUID userId = userRepository.save(user).getId();
        String originalStorageKey = user.getSignatureOriginalStorageKey();
        String thumbnailStorageKey = user.getSignatureThumbnailStorageKey();
        String originalImageUrl = "https://cdn.test.chalkak/" + originalStorageKey;
        String thumbnailImageUrl = "https://cdn.test.chalkak/" + thumbnailStorageKey;
        flushAndClear();

        given(signatureImageStorage.toImageUrl(originalStorageKey))
                .willReturn(originalImageUrl);
        given(signatureImageStorage.toImageUrl(thumbnailStorageKey))
                .willReturn(thumbnailImageUrl);

        // When
        UserSignatureResult result = userService.getSignature(userId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(userId).orElseThrow();

        assertThat(result.originalImageUrl()).isEqualTo(originalImageUrl);
        assertThat(result.thumbnailImageUrl()).isEqualTo(thumbnailImageUrl);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
    }

    @Test
    @DisplayName("이미 실패한 사인을 조회하면 재등록 오류를 발생시킨다")
    void getSignature_failedPending_throwsBusinessException() {
        // Given
        User user = UserFixture.create();
        UUID uploadId = UUID.randomUUID();
        user.startSignatureProcessing(uploadId, Instant.now());
        user.failSignatureProcessing(uploadId);
        UUID userId = userRepository.save(user).getId();
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userService.getSignature(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.SIGNATURE_REGISTRATION_REQUIRED));
    }

    @Test
    @DisplayName("사인을 조회할 회원이 없으면 인증 실패로 거부한다")
    void getSignature_notExistingUser_throwsUnauthorizedException() {
        // Given
        UUID userId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> userService.getSignature(userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }

    @Test
    @DisplayName("사인 수정을 요청하면 기존 활성 사인을 유지하고 pending 정보를 저장한다")
    void updateSignature_uploadedImage_startsProcessingAndPreservesActiveSignature() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);
        String activeImageUrl =
                "https://cdn.test.chalkak/" + activeOriginalStorageKey;

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn(activeImageUrl);

        // When
        String imageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId())
                .isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
        assertThat(updated.getSignatureProcessingStartedAt()).isNotNull();
        assertThat(imageUrl).isEqualTo(activeImageUrl);
    }

    @Test
    @DisplayName("Lambda가 등록 API보다 먼저 완료했으면 pending 저장 후 즉시 승격한다")
    void updateSignature_alreadyProcessedImage_promotesImmediately() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);
        String completedImageUrl =
                "https://cdn.test.chalkak/signatures/dev/original/"
                        + uploadId
                        + ".png";

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.isProcessingCompleted(uploadId))
                .willReturn(true);
        given(signatureImageStorage.toImageUrl(
                storageKeys.originalStorageKey()))
                .willReturn(completedImageUrl);

        // When
        String imageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(storageKeys.originalStorageKey());
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(updated.getPendingSignatureUploadId()).isNull();
        assertThat(updated.getSignatureProcessingStatus()).isNull();
        assertThat(updated.getSignatureProcessingStartedAt()).isNull();
        assertThat(imageUrl).isEqualTo(completedImageUrl);
    }

    @Test
    @DisplayName("성공 콜백은 pending 사인을 활성 사인으로 승격한다")
    void completeSignatureProcessing_matchingPending_promotesSignature() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));

        userService.updateSignature(id, uploadId);
        flushAndClear();

        // When
        userService.completeSignatureProcessing(uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(storageKeys.originalStorageKey());
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(updated.getPendingSignatureUploadId()).isNull();
        assertThat(updated.getSignatureProcessingStatus()).isNull();
        assertThat(updated.getSignatureProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("제한 시간을 넘겨 도착한 성공 콜백은 무시한다")
    void completeSignatureProcessing_timedOutPending_ignoresCallback() {
        // Given
        User user = UserFixture.create();
        UUID uploadId = UUID.randomUUID();
        String activeOriginalStorageKey = user.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey = user.getSignatureThumbnailStorageKey();
        user.startSignatureProcessing(
                uploadId,
                Instant.now().minus(Duration.ofMinutes(8)));
        UUID userId = userRepository.save(user).getId();
        flushAndClear();

        // When
        userService.completeSignatureProcessing(uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(userId).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId()).isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
        assertThat(updated.getSignatureProcessingStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패 콜백은 기존 활성 사인을 유지하고 처리 상태만 실패로 바꾼다")
    void failSignatureProcessing_matchingPending_preservesActiveSignature() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));

        userService.updateSignature(id, uploadId);
        flushAndClear();

        // When
        userService.failSignatureProcessing(uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId())
                .isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.FAILED);
        assertThat(updated.getSignatureProcessingStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("현재 pending과 다른 업로드의 느린 성공 콜백은 무시한다")
    void completeSignatureProcessing_staleUpload_ignoresCallback() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        // When
        userService.completeSignatureProcessing(UUID.randomUUID());
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
    }

    @Test
    @DisplayName("처리 중인 같은 사인 업로드를 다시 요청해도 같은 활성 이미지 URL을 반환한다")
    void updateSignature_samePendingUploadIdTwice_returnsSameActiveImageUrl() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);
        String activeImageUrl =
                "https://cdn.test.chalkak/" + activeOriginalStorageKey;

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn(activeImageUrl);

        // When
        String firstImageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        Instant firstStartedAt = userRepository.findById(id)
                .orElseThrow()
                .getSignatureProcessingStartedAt();

        String secondImageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(firstImageUrl).isEqualTo(activeImageUrl);
        assertThat(secondImageUrl).isEqualTo(activeImageUrl);
        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId())
                .isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
        assertThat(updated.getSignatureProcessingStartedAt())
                .isEqualTo(firstStartedAt);
    }

    @Test
    @DisplayName("타임아웃된 같은 사인 업로드를 다시 요청하면 새 업로드가 필요하다고 안내한다")
    void updateSignature_sameTimedOutUploadId_throwsReuploadRequiredException() {
        // Given
        User user = UserFixture.create();
        UUID uploadId = UUID.randomUUID();
        String activeOriginalStorageKey = user.getSignatureOriginalStorageKey();
        user.startSignatureProcessing(
                uploadId,
                Instant.now().minus(Duration.ofMinutes(8)));
        UUID userId = userRepository.save(user).getId();
        flushAndClear();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(userId, uploadId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사인 이미지 처리에 실패했습니다. "
                        + "새로운 업로드 ID를 발급받아 이미지를 다시 업로드해 주세요.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.SIGNATURE_REUPLOAD_REQUIRED));

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
    }

    @Test
    @DisplayName("타임아웃 후 새로운 사인 업로드를 요청하면 새 처리를 시작한다")
    void updateSignature_newUploadAfterTimeout_startsNewProcessing() {
        // Given
        User user = UserFixture.create();
        UUID timedOutUploadId = UUID.randomUUID();
        Instant timedOutStartedAt = Instant.now().minus(Duration.ofMinutes(8));
        String activeOriginalStorageKey = user.getSignatureOriginalStorageKey();
        user.startSignatureProcessing(timedOutUploadId, timedOutStartedAt);
        UUID userId = userRepository.save(user).getId();
        flushAndClear();

        UUID newUploadId = UUID.randomUUID();
        given(signatureImageStorage.toStorageKeys(newUploadId))
                .willReturn(storageKeys(newUploadId));
        given(signatureImageStorage.findUploadedImage(newUploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn("https://cdn.test.chalkak/" + activeOriginalStorageKey);

        // When
        userService.updateSignature(userId, newUploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(userId).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getPendingSignatureUploadId()).isEqualTo(newUploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
        assertThat(updated.getSignatureProcessingStartedAt())
                .isAfter(timedOutStartedAt);
    }

    @Test
    @DisplayName("실패한 사인 업로드를 다시 요청하면 새 업로드가 필요하다고 안내한다")
    void updateSignature_sameFailedUploadId_throwsReuploadRequiredException() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn("https://cdn.test.chalkak/" + activeOriginalStorageKey);

        userService.updateSignature(id, uploadId);
        flushAndClear();

        userService.failSignatureProcessing(uploadId);
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사인 이미지 처리에 실패했습니다. "
                        + "새로운 업로드 ID를 발급받아 이미지를 다시 업로드해 주세요.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.SIGNATURE_REUPLOAD_REQUIRED));

        User updated = userRepository.findById(id).orElseThrow();
        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("이미 반영된 사인을 다시 요청해도 썸네일 키를 유지한다")
    void updateSignature_storageKeyAlreadyOwnedByUser_keepsThumbnailKey() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String originalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String thumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(new SignatureStorageKeys(
                        originalStorageKey,
                        "chalkak/signatures/dev/thumbnail/"
                                + uploadId
                                + ".png"));
        given(signatureImageStorage.toImageUrl(originalStorageKey))
                .willReturn(
                        "https://cdn.test.chalkak/" + originalStorageKey);

        // When
        userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(originalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(thumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId()).isNull();
    }

    @Test
    @DisplayName("다른 회원이 이미 쓴 사인 키는 사용할 수 없다")
    void updateSignature_alreadyUsedStorageKey_throwsNotFoundException() {
        // Given
        User owner = userRepository.save(UserFixture.create());
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(new SignatureStorageKeys(
                        owner.getSignatureOriginalStorageKey(),
                        "chalkak/signatures/dev/thumbnail/"
                                + uploadId
                                + ".png"));

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("업로드한 사인 이미지를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("업로드되지 않은 사인 이미지는 사용할 수 없다")
    void updateSignature_notUploadedImage_throwsNotFoundException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("업로드한 사인 이미지를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("허용하지 않는 형식의 사인 이미지는 사용할 수 없다")
    void updateSignature_disallowedContentType_throwsBusinessException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(
                        new StoredImageMetadata("image/jpeg", 1024L)));

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용할 수 없는 사인 이미지입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 회원의 사인 교체는 인증 실패로 거부한다")
    void updateSignature_notExistingUser_throwsUnauthorizedException() {
        // Given
        UUID notExistingId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));

        // When & Then
        assertThatThrownBy(() ->
                userService.updateSignature(notExistingId, uploadId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }

    @Test
    @DisplayName("탈퇴한 회원의 사인 교체는 인증 실패로 거부한다")
    void updateSignature_withdrawnUser_throwsUnauthorizedException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        userService.withdraw(id, List.of());
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }

    @Test
    @DisplayName("현재 활성 사인으로 되돌리면 진행 중이던 pending을 취소한다")
    void updateSignature_revertToActiveSignature_cancelsPendingProcessing() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID pendingUploadId = UUID.randomUUID();
        UUID activeUploadId = UUID.randomUUID();
        String activeImageUrl =
                "https://cdn.test.chalkak/" + activeOriginalStorageKey;

        given(signatureImageStorage.toStorageKeys(pendingUploadId))
                .willReturn(storageKeys(pendingUploadId));
        given(signatureImageStorage.findUploadedImage(pendingUploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toStorageKeys(activeUploadId))
                .willReturn(new SignatureStorageKeys(
                        activeOriginalStorageKey,
                        activeThumbnailStorageKey));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn(activeImageUrl);

        userService.updateSignature(id, pendingUploadId);
        flushAndClear();

        // When
        String imageUrl = userService.updateSignature(id, activeUploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(imageUrl).isEqualTo(activeImageUrl);
        assertThat(updated.getPendingSignatureUploadId()).isNull();
        assertThat(updated.getSignatureProcessingStatus()).isNull();
        assertThat(updated.getSignatureProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("취소된 pending의 뒤늦은 성공 콜백은 활성 사인을 바꾸지 못한다")
    void completeSignatureProcessing_cancelledPending_doesNotOverwriteActive() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID pendingUploadId = UUID.randomUUID();
        UUID activeUploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(pendingUploadId))
                .willReturn(storageKeys(pendingUploadId));
        given(signatureImageStorage.findUploadedImage(pendingUploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toStorageKeys(activeUploadId))
                .willReturn(new SignatureStorageKeys(
                        activeOriginalStorageKey,
                        activeThumbnailStorageKey));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn("https://cdn.test.chalkak/" + activeOriginalStorageKey);

        userService.updateSignature(id, pendingUploadId);
        flushAndClear();
        userService.updateSignature(id, activeUploadId);
        flushAndClear();

        // When
        userService.completeSignatureProcessing(pendingUploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId()).isNull();
    }

    @Test
    @DisplayName("같은 성공 콜백이 두 번 도착해도 첫 승격 결과를 유지한다")
    void completeSignatureProcessing_duplicateCallback_isIdempotent() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(saved.getSignatureOriginalStorageKey()))
                .willReturn("https://cdn.test.chalkak/active.png");

        userService.updateSignature(id, uploadId);
        flushAndClear();
        userService.completeSignatureProcessing(uploadId);
        flushAndClear();

        // When
        userService.completeSignatureProcessing(uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(storageKeys.originalStorageKey());
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(updated.getPendingSignatureUploadId()).isNull();
        assertThat(updated.getSignatureProcessingStatus()).isNull();
    }

    @Test
    @DisplayName("승격이 끝난 뒤 도착한 실패 콜백은 활성 사인을 훼손하지 않는다")
    void failSignatureProcessing_afterPromotion_keepsActiveSignature() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(saved.getSignatureOriginalStorageKey()))
                .willReturn("https://cdn.test.chalkak/active.png");

        userService.updateSignature(id, uploadId);
        flushAndClear();
        userService.completeSignatureProcessing(uploadId);
        flushAndClear();

        // When
        userService.failSignatureProcessing(uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(storageKeys.originalStorageKey());
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(updated.getSignatureProcessingStatus()).isNull();
    }

    @Test
    @DisplayName("영구 실패가 확정된 뒤 도착한 성공 콜백은 승격하지 않는다")
    void completeSignatureProcessing_afterPermanentFailure_doesNotPromote() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn("https://cdn.test.chalkak/" + activeOriginalStorageKey);

        userService.updateSignature(id, uploadId);
        flushAndClear();
        userService.failSignatureProcessing(uploadId);
        flushAndClear();

        // When
        userService.completeSignatureProcessing(uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.FAILED);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private int countLiveRefreshTokens(UUID userId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE user_id = ? AND revoked_at IS NULL
                """, Integer.class, userId);
    }

    private Timestamp findRefreshTokenRevokedAt(String token) {
        return jdbcTemplate.queryForObject("""
                        SELECT revoked_at FROM user_refresh_tokens WHERE token_hash = ?
                        """,
                Timestamp.class,
                refreshTokenHasher.encode(token));
    }

    private SignatureStorageKeys storageKeys(UUID uploadId) {
        return new SignatureStorageKeys(
                "chalkak/signatures/dev/original/"
                        + uploadId
                        + ".png",
                "chalkak/signatures/dev/thumbnail/"
                        + uploadId
                        + ".png");
    }

    @Test
    @DisplayName("차단 회원이 탈퇴하면 재가입 차단을 위해 소셜 계정을 유지한다")
    void withdraw_bannedUser_preservesSocialAccount() {
        // Given
        User user = userRepository.save(UserFixture.createBanned(null));
        UUID userId = user.getId();
        String subject = "banned-google-subject";
        String subjectHmac = fingerprintEncoder.encode(
                SocialProvider.GOOGLE,
                subject);
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac));
        flushAndClear();

        // When
        userService.withdraw(userId, List.of());
        flushAndClear();

        // Then
        assertThat(userRepository.findActiveById(userId)).isEmpty();
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        SocialProvider.GOOGLE,
                        subjectHmac)
                .orElseThrow();
        assertThat(socialAccount.getUser().getId()).isEqualTo(userId);
        assertThat(socialAccount.getUser().isDeleted()).isTrue();
        assertThat(socialAccount.getUser().getStatus())
                .isEqualTo(UserStatus.BANNED);
    }

    @Test
    @DisplayName("Apple 회원이 탈퇴하면 Apple 인증 정보가 삭제된다")
    void withdraw_appleUser_deletesAppleAuthorization() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.APPLE,
                fingerprintEncoder.encode(SocialProvider.APPLE, "apple-subject")));
        UUID socialAccountId = socialAccount.getId();
        AppleAuthorization authorization = appleAuthorizationRepository.save(
                AppleAuthorization.create(
                        socialAccount,
                        "encrypted-refresh-token"));
        flushAndClear();

        // When
        userService.withdraw(user.getId(), List.of(new AppleAuthorizationSnapshot(
                authorization.getId(),
                "encrypted-refresh-token")));
        flushAndClear();

        // Then
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .isEmpty();
    }

    @Test
    @DisplayName("스냅샷과 실제 저장된 인증 정보가 다르면 삭제 없이 예외가 발생한다")
    void withdraw_authorizationSnapshotMismatch_throwsBusinessExceptionWithoutDeleting() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.APPLE,
                fingerprintEncoder.encode(SocialProvider.APPLE, "apple-subject")));
        UUID socialAccountId = socialAccount.getId();
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                "encrypted-refresh-token"));
        flushAndClear();

        // When & Then
        // 실제로는 인증 정보가 하나 저장돼 있는데, 아무것도 폐기하지 않은 것으로(빈 스냅샷)
        // 탈퇴를 시도하는 상황이다. 실제 트랜잭션 밖에서 다른 로그인이 끼어든 것과 같은 대조
        // 실패를 낸다.
        assertThatThrownBy(() -> userService.withdraw(user.getId(), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_STATE_CHANGED)
                .hasMessage("탈퇴 처리 중 Apple 인증 정보가 변경되었습니다. 다시 시도해 주세요.");

        assertThat(userRepository.findActiveById(user.getId())).isPresent();
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .hasSize(1);
    }

    @Test
    @DisplayName("차단된 Apple 회원이 탈퇴하면 소셜 계정은 남기고 Apple 인증 정보만 삭제한다")
    void withdraw_bannedAppleUser_deletesAppleAuthorizationOnly() {
        // Given
        User user = userRepository.save(UserFixture.createBanned(null));
        String subjectHmac = fingerprintEncoder.encode(
                SocialProvider.APPLE,
                "banned-apple-subject");
        SocialAccount socialAccount = socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.APPLE,
                subjectHmac));
        UUID socialAccountId = socialAccount.getId();
        AppleAuthorization authorization = appleAuthorizationRepository.save(
                AppleAuthorization.create(
                        socialAccount,
                        "encrypted-refresh-token"));
        flushAndClear();

        // When
        userService.withdraw(user.getId(), List.of(new AppleAuthorizationSnapshot(
                authorization.getId(),
                "encrypted-refresh-token")));
        flushAndClear();

        // Then
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .isEmpty();
        assertThat(socialAccountRepository.findByProviderAndSubjectHmac(
                SocialProvider.APPLE,
                subjectHmac))
                .isPresent();
    }
}
