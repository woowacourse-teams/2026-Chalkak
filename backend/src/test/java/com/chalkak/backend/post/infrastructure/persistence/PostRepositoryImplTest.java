package com.chalkak.backend.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.post.domain.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PostRepositoryImplTest {

    private final PostJpaRepository postJpaRepository = mock(PostJpaRepository.class);
    private final PostRepositoryImpl postRepository = new PostRepositoryImpl(postJpaRepository);

    @Test
    @DisplayName("알 수 없는 무결성 오류는 중복 요청으로 변환하지 않고 전파한다")
    void save_unknownIntegrityViolation_propagatesOriginalException() {
        // Given
        Post post = mock(Post.class);
        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException("unexpected database failure");
        given(postJpaRepository.saveAndFlush(post)).willThrow(databaseFailure);

        // When & Then
        assertThatThrownBy(() -> postRepository.save(post)).isSameAs(databaseFailure);
    }
}
