package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.token.UserRefreshTokenService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657aa4");

    @InjectMocks
    private UserService service;
    @Mock
    private UserRepository users;
    @Mock
    private SocialAccountRepository accounts;
    @Mock
    private PostRepository posts;
    @Mock
    private UserRefreshTokenService refreshTokens;
    @Mock
    private SignatureImageUploadIssuer issuer;
    @Mock
    private SignatureImageStorage storage;

    @Test
    @DisplayName("정지된 작가의 신청은 좋아요 집계 전에 정지 오류로 거절한다")
    void applyForArtist_bannedArtist_rejectsBeforeCountingLikes() {
        // Given
        User user = UserFixture.create(USER_ID);
        user.grantArtistRole();
        user.ban();
        given(users.findActiveByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> service.applyForArtist(USER_ID))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(posts);
    }

    @Test
    @DisplayName("이미 작가인 회원의 신청은 좋아요 집계 전에 거절한다")
    void applyForArtist_existingArtist_rejectsBeforeCountingLikes() {
        // Given
        User user = UserFixture.create(USER_ID);
        user.grantArtistRole();
        given(users.findActiveByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> service.applyForArtist(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED));
        verifyNoInteractions(posts);
    }

    @Test
    @DisplayName("활성 회원의 사인 업로드는 새 ID로 발급한 URL과 유효 시간을 반환한다")
    void createSignatureUpload_activeUser_returnsIssuedUpload() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(issuer.issue(any(UUID.class))).willAnswer(invocation -> new SignatureImageUpload(
                invocation.getArgument(0),
                "https://s3.example.com/presigned",
                300L
        ));

        // When
        SignatureImageUpload result = service.createSignatureUpload(USER_ID);

        // Then
        assertThat(result.uploadId()).isNotNull();
        assertThat(result.uploadUrl()).isEqualTo("https://s3.example.com/presigned");
        assertThat(result.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    @DisplayName("처리 중인 사인이 없으면 현재 원본과 썸네일 URL을 반환한다")
    void getSignature_withoutPending_returnsCurrentSignatureUrls() {
        // Given
        User user = UserFixture.create(USER_ID);
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(user));
        given(storage.toImageUrl(user.getSignatureOriginalStorageKey()))
                .willReturn("https://cdn.test/original");
        given(storage.toImageUrl(user.getSignatureThumbnailStorageKey()))
                .willReturn("https://cdn.test/thumbnail");

        // When
        UserSignatureResult result = service.getSignature(USER_ID);

        // Then
        assertThat(result.originalImageUrl()).isEqualTo("https://cdn.test/original");
        assertThat(result.thumbnailImageUrl()).isEqualTo("https://cdn.test/thumbnail");
    }

    @Test
    @DisplayName("활성 회원이 없으면 사인 업로드 발급을 부수 효과 없이 거부한다")
    void createSignatureUpload_unavailableUser_throwsUnauthorizedException() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.createSignatureUpload(USER_ID))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
        verifyNoInteractions(
                issuer,
                storage,
                accounts,
                refreshTokens
        );
    }

    @Test
    @DisplayName("활성 회원이 없으면 사인 조회를 부수 효과 없이 거부한다")
    void getSignature_unavailableUser_throwsUnauthorizedException() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getSignature(USER_ID))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
        verifyNoInteractions(
                issuer,
                storage,
                accounts,
                refreshTokens
        );
    }

    @Test
    @DisplayName("활성 회원이 없으면 탈퇴를 부수 효과 없이 거부한다")
    void completeWithdrawal_unavailableUser_throwsUnauthorizedException() {
        // Given
        given(users.findActiveByIdForUpdate(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.completeWithdrawal(USER_ID, List.of()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
        verifyNoInteractions(
                issuer,
                storage,
                accounts,
                refreshTokens
        );
    }
}
