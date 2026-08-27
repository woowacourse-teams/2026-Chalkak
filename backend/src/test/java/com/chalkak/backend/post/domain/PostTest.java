package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.user.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostTest {

    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f1");

    private final User author = mock(User.class);
    private final Topic topic = mock(Topic.class);
    private final Photo photo = Photo.createPhoto("chalkak/posts/dev/original/upload-id.png");

    @Test
    @DisplayName("작성자와 주제와 사진으로 검수 중인 게시물을 생성한다")
    void createPost_validValues_createsValidatingPost() {
        // When
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "오늘의 기록");

        // Then
        assertThat(post.getAuthor()).isSameAs(author);
        assertThat(post.getTopic()).isSameAs(topic);
        assertThat(post.getPhoto()).isSameAs(photo);
        assertThat(post.getTitle()).isEqualTo("오늘의 기록");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.VALIDATING);
    }

    @Test
    @DisplayName("제목이 공백이면 제목 없는 게시물로 생성한다")
    void createPost_blankTitle_normalizesTitleToNull() {
        // When
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "   ");

        // Then
        assertThat(post.getTitle()).isNull();
    }

    @Test
    @DisplayName("제목이 10자면 게시물을 생성한다")
    void createPost_tenCharacterTitle_createsPost() {
        // Given
        String title = "1234567890";

        // When
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, title);

        // Then
        assertThat(post.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("10자를 넘는 유니코드 공백 제목도 제목 없는 게시물로 생성한다")
    void createPost_longBlankTitle_normalizesTitleToNull() {
        // Given
        String title = "　".repeat(11);

        // When
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, title);

        // Then
        assertThat(post.getTitle()).isNull();
    }

    @Test
    @DisplayName("이모지 제목은 코드 포인트 기준으로 10자까지 허용한다")
    void createPost_tenCodePointEmojiTitle_createsPost() {
        // Given
        String title = "📸".repeat(10);

        // When
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, title);

        // Then
        assertThat(post.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("제목이 10자를 초과하면 게시물을 생성할 수 없다")
    void createPost_tooLongTitle_throwsBusinessException() {
        // Given
        String title = "12345678901";

        // When & Then
        assertThatThrownBy(() -> Post.createPost(author, topic, photo, UPLOAD_ID, title))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제목은 10자 이하여야 합니다.");
    }
    @Test
    @DisplayName("이미지 처리가 끝나면 관리자 검수 대기 상태가 된다")
    void requestModeration_validatingPost_becomesPending() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");

        // When
        post.requestModeration();

        // Then
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(post.getModeratedAt()).isNull();
    }

    @Test
    @DisplayName("관리자 검수 대기 게시물을 승인하면 APPROVED가 되고 검수 시각을 기록한다")
    void approve_pendingPost_becomesApproved() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");
        post.requestModeration();
        Instant moderatedAt = Instant.parse("2026-08-20T00:00:00Z");

        // When
        post.approve(moderatedAt);

        // Then
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(post.getModeratedAt()).isEqualTo(moderatedAt);
    }

    @Test
    @DisplayName("관리자 검수 대기 게시물을 거절하면 REJECTED가 되고 검수 시각을 기록한다")
    void reject_pendingPost_becomesRejected() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");
        post.requestModeration();
        Instant moderatedAt = Instant.parse("2026-08-20T00:00:00Z");

        // When
        post.reject(moderatedAt);

        // Then
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(post.getModeratedAt()).isEqualTo(moderatedAt);
    }

    @Test
    @DisplayName("이미지 처리에 실패하면 관리자 결정 시각 없이 REJECTED가 된다")
    void failImageProcessing_validatingPost_becomesRejectedWithoutModerationTime() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");

        // When
        post.failImageProcessing();

        // Then
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(post.getModeratedAt()).isNull();
    }

    @Test
    @DisplayName("이미지 처리 중인 게시물은 관리자가 승인할 수 없다")
    void approve_validatingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");

        // When & Then
        assertThatThrownBy(() -> post.approve(Instant.parse("2026-08-20T00:00:00Z")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.VALIDATING);
    }

    @Test
    @DisplayName("이미지 처리 중인 게시물은 관리자가 거절할 수 없다")
    void reject_validatingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");

        // When & Then
        assertThatThrownBy(() -> post.reject(Instant.parse("2026-08-20T00:00:00Z")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.VALIDATING);
    }

    @Test
    @DisplayName("관리자 검수 대기 이후에는 이미지 실패 상태로 바꿀 수 없다")
    void failImageProcessing_pendingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");
        post.requestModeration();

        // When & Then
        assertThatThrownBy(post::failImageProcessing)
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    @DisplayName("이미 관리자 검수 대기인 게시물은 검수 요청을 반복할 수 없다")
    void requestModeration_pendingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");
        post.requestModeration();

        // When & Then
        assertThatThrownBy(post::requestModeration)
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
    }

    @Test
    @DisplayName("이미 검수가 끝난 게시물은 상태를 다시 변경할 수 없다")
    void approve_rejectedPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");
        post.requestModeration();
        post.reject(Instant.parse("2026-08-20T00:00:00Z"));

        // When & Then
        assertThatThrownBy(() -> post.approve(Instant.parse("2026-08-20T00:01:00Z")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
    }

    @Test
    @DisplayName("관리자 검수 시각이 없으면 승인할 수 없다")
    void approve_withoutModeratedAt_throwsBusinessException() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");
        post.requestModeration();

        // When & Then
        assertThatThrownBy(() -> post.approve(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 시각이 필요합니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    @DisplayName("관리자 검수 시각이 없으면 거절할 수 없다")
    void reject_withoutModeratedAt_throwsBusinessException() {
        // Given
        Post post = Post.createPost(author, topic, photo, UPLOAD_ID, "제목");
        post.requestModeration();

        // When & Then
        assertThatThrownBy(() -> post.reject(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 시각이 필요합니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    }
}
