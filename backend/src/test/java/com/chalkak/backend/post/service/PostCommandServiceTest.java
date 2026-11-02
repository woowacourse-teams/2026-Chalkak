package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
import com.chalkak.backend.post.repository.PostImageUploadRepository;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PresignedPostImageUpload;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a4");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b4");
    private static final UUID UPLOAD_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c4");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @InjectMocks
    private PostCommandService service;
    @Mock
    private UserRepository users;
    @Mock
    private TopicRepository topics;
    @Mock
    private PostRepository posts;
    @Mock
    private PostImageUploadRepository uploads;
    @Mock
    private PostImageUploadIssuer issuer;

    @Test
    @DisplayName("발급할 업로드의 회원, 초기 상태, 만료와 저장 ID로 요청한 URL을 반환한다")
    void createPostImageUpload_activeUser_savesIssuedUploadAndReturnsPresignedUrl() {
        // Given
        User user = UserFixture.create(USER_ID);
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(user));
        given(uploads.save(any())).willAnswer(invocation -> {
            PostImageUpload upload = invocation.getArgument(0);
            ReflectionTestUtils.setField(
                    upload,
                    "id",
                    UPLOAD_ID
            );
            return upload;
        });
        given(issuer.issue(UPLOAD_ID)).willReturn(new PresignedPostImageUpload(
                "https://test-bucket.s3.amazonaws.com/upload",
                300L,
                "image/webp",
                5_242_880L
        ));
        Instant before = Instant.now();

        // When
        PostImageUploadResult result = service.createPostImageUpload(USER_ID);

        // Then
        assertThat(result.uploadId()).isEqualTo(UPLOAD_ID);
        assertThat(result.uploadUrl()).isEqualTo("https://test-bucket.s3.amazonaws.com/upload");
        assertThat(result.expiresInSeconds()).isEqualTo(300L);
        assertThat(result.contentType()).isEqualTo("image/webp");
        assertThat(result.maxBytes()).isEqualTo(5_242_880L);
        ArgumentCaptor<PostImageUpload> saved = ArgumentCaptor.forClass(PostImageUpload.class);
        verify(uploads).save(saved.capture());
        assertThat(saved.getValue().getUser().getId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getStatus()).isEqualTo(PostImageUploadStatus.ISSUED);
        assertThat(saved.getValue().getRejectionReason()).isNull();
        assertThat(saved.getValue().getImageMetadata()).isNull();
        assertThat(saved.getValue().getClaimedAt()).isNull();
        assertThat(saved.getValue().getExpiresAt()).isBetween(
                before.plus(PostImageUpload.CLAIM_TTL),
                Instant.now().plus(PostImageUpload.CLAIM_TTL));
    }

    @Test
    @DisplayName("활성 조회에서 제외된 회원은 업로드 발급 전에 거부한다")
    void createPostImageUpload_unavailableUser_throwsUnauthorized() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.createPostImageUpload(USER_ID))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
        verifyNoInteractions(uploads, issuer);
    }

    @Test
    @DisplayName("활성 조회에서 제외된 작성자는 게시물 생성 전에 거부한다")
    void createPost_unavailableUser_throwsUnauthorizedException() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "제목"
        )).isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.")
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(
                topics,
                uploads,
                posts
        );
    }

    @Test
    @DisplayName("활성 주제가 없으면 업로드를 사용하거나 게시물을 저장하지 않는다")
    void createPost_unavailableTopic_throwsNotFoundException() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(topics.findActiveById(TOPIC_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "제목"
        )).isInstanceOf(NotFoundException.class).hasMessage("게시물을 작성할 주제를 찾을 수 없습니다.");
        verifyNoInteractions(uploads, posts);
    }

    @ParameterizedTest
    @ValueSource(ints = {2020, 2100})
    @DisplayName("아직 열리지 않았거나 종료된 주제는 업로드 사용 전에 거부한다")
    void createPost_topicNotOpen_throwsBusinessException(int year) {
        // Given
        Instant start = LocalDate.ofYearDay(year, 1).atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant();
        Topic topic = Topic.create(
                "참여 주제",
                LocalDate.ofYearDay(year, 1),
                new ParticipationPeriod(start, start.plusSeconds(3600)),
                start.minusSeconds(3600)
        );
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(topics.findActiveById(TOPIC_ID)).willReturn(Optional.of(topic));

        // When & Then
        assertThatThrownBy(() -> service.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "제목"
        )).isInstanceOf(BusinessException.class).hasMessage("현재 게시물을 작성할 수 없는 주제입니다.");
        verifyNoInteractions(uploads, posts);
    }

    @Test
    @DisplayName("삭제되거나 없는 게시물은 제목을 수정할 수 없다")
    void updatePost_unavailablePost_throwsNotFoundException() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(posts.findActiveByIdForUpdate(POST_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.updatePost(
                USER_ID,
                POST_ID,
                "수정 제목"
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("게시물을 찾을 수 없습니다.")
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("탈퇴한 작성자는 게시물 조회와 수정 전에 거부한다")
    void updatePost_unavailableUser_throwsUnauthorizedException() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.updatePost(
                USER_ID,
                POST_ID,
                "수정 제목"
        )).isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.")
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(posts);
    }

    @Test
    @DisplayName("없는 게시물의 삭제는 거부한다")
    void deletePost_unknownPost_throwsNotFoundException() {
        // Given
        given(posts.findByIdForUpdate(POST_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.deletePost(USER_ID, POST_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("게시물을 찾을 수 없습니다.")
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_ERROR);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("없는 업로드의 성공 또는 실패 콜백은 게시물을 변경하지 않는다")
    void processImage_unknownUpload_doesNothing(boolean success) {
        // Given
        given(uploads.findByIdForUpdate(UPLOAD_ID)).willReturn(Optional.empty());

        // When
        if (success) {
            service.completePostImageProcessing(UPLOAD_ID, java.util.Map.of());
        } else {
            service.failPostImageProcessing(UPLOAD_ID, "검수 실패");
        }

        // Then
        verifyNoInteractions(posts);
    }
}
