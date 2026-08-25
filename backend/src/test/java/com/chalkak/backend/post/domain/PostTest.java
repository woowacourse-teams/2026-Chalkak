package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostTest {

    private final User author = mock(User.class);
    private final Topic topic = mock(Topic.class);
    private final Photo photo = Photo.createPhoto("chalkak/posts/dev/original/upload-id.png");

    @Test
    @DisplayName("작성자와 주제와 사진으로 검수 중인 게시물을 생성한다")
    void createPost_validValues_createsValidatingPost() {
        // When
        Post post = Post.createPost(author, topic, photo, "오늘의 기록");

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
        Post post = Post.createPost(author, topic, photo, "   ");

        // Then
        assertThat(post.getTitle()).isNull();
    }

    @Test
    @DisplayName("제목이 10자면 게시물을 생성한다")
    void createPost_tenCharacterTitle_createsPost() {
        // Given
        String title = "1234567890";

        // When
        Post post = Post.createPost(author, topic, photo, title);

        // Then
        assertThat(post.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("10자를 넘는 유니코드 공백 제목도 제목 없는 게시물로 생성한다")
    void createPost_longBlankTitle_normalizesTitleToNull() {
        // Given
        String title = "　".repeat(11);

        // When
        Post post = Post.createPost(author, topic, photo, title);

        // Then
        assertThat(post.getTitle()).isNull();
    }

    @Test
    @DisplayName("이모지 제목은 코드 포인트 기준으로 10자까지 허용한다")
    void createPost_tenCodePointEmojiTitle_createsPost() {
        // Given
        String title = "📸".repeat(10);

        // When
        Post post = Post.createPost(author, topic, photo, title);

        // Then
        assertThat(post.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("제목이 10자를 초과하면 게시물을 생성할 수 없다")
    void createPost_tooLongTitle_throwsBusinessException() {
        // Given
        String title = "12345678901";

        // When & Then
        assertThatThrownBy(() -> Post.createPost(author, topic, photo, title))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제목은 10자 이하여야 합니다.");
    }
}
