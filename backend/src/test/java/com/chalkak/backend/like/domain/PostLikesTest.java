package com.chalkak.backend.like.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class PostLikesTest {

    @Test
    @DisplayName("집계가 있는 게시물의 좋아요 수와 조회자의 좋아요 여부를 반환한다")
    void getLikeCount_existingCounter_returnsCountAndViewerState() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID unknownPostId = UUID.randomUUID();
        PostLikes postLikes = new PostLikes(Map.of(postId, 2L), Set.of(postId));

        // When & Then
        assertThat(postLikes.getLikeCount(postId)).isEqualTo(2L);
        assertThat(postLikes.isLiked(postId)).isTrue();
        assertThat(postLikes.isLiked(unknownPostId)).isFalse();
    }

    @Test
    @DisplayName("빈 집계에서 개수를 요청하면 누락 오류를 내고 내 좋아요 여부는 false다")
    void createEmpty_anyPost_throwsForMissingCountAndReturnsFalse() {
        // Given
        UUID postId = UUID.randomUUID();

        // When
        PostLikes postLikes = PostLikes.createEmpty();

        // Then
        assertThatThrownBy(() -> postLikes.getLikeCount(postId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("좋아요 집계 행을 찾을 수 없습니다: " + postId);
        assertThat(postLikes.isLiked(postId)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 100, 2147483648L})
    @DisplayName("집계 행이 있으면 0을 포함한 저장된 실제 개수를 반환한다")
    void getLikeCount_existingCounter_returnsStoredCount(long count) {
        // Given
        UUID postId = UUID.randomUUID();
        PostLikes postLikes = new PostLikes(Map.of(postId, count), Set.of());

        // When & Then
        assertThat(postLikes.getLikeCount(postId)).isEqualTo(count);
    }

    @Test
    @DisplayName("다른 게시물의 집계가 있어도 요청한 집계가 없으면 0 대신 누락 오류를 낸다")
    void getLikeCount_missingCounter_throwsIntegrityException() {
        // Given
        UUID postId = UUID.randomUUID();
        PostLikes postLikes = new PostLikes(Map.of(UUID.randomUUID(), 2L), Set.of());

        // When & Then
        assertThatThrownBy(() -> postLikes.getLikeCount(postId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("좋아요 집계 행을 찾을 수 없습니다: " + postId);
    }

    @Test
    @DisplayName("원본 컬렉션이 바뀌어도 생성한 좋아요 집계는 유지한다")
    void create_sourceCollectionsChanged_keepsSnapshot() {
        // Given
        UUID postId = UUID.randomUUID();
        Map<UUID, Long> counts = new HashMap<>(Map.of(postId, 2L));
        Set<UUID> likedPostIds = new HashSet<>(Set.of(postId));
        PostLikes postLikes = new PostLikes(counts, likedPostIds);

        // When
        counts.clear();
        likedPostIds.clear();

        // Then
        assertThat(postLikes.getLikeCount(postId)).isEqualTo(2L);
        assertThat(postLikes.isLiked(postId)).isTrue();
    }
}
