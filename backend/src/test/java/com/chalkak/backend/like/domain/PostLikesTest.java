package com.chalkak.backend.like.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostLikesTest {

    @Test
    @DisplayName("게시물별 좋아요를 조회하고 없는 집계는 0과 false로 반환한다")
    void getLikeCount_knownAndUnknownPost_returnsCountAndViewerState() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID unknownPostId = UUID.randomUUID();
        PostLikes postLikes = new PostLikes(Map.of(postId, 2L), Set.of(postId));

        // When & Then
        assertThat(postLikes.getLikeCount(postId)).isEqualTo(2L);
        assertThat(postLikes.isLiked(postId)).isTrue();
        assertThat(postLikes.getLikeCount(unknownPostId)).isZero();
        assertThat(postLikes.isLiked(unknownPostId)).isFalse();
    }

    @Test
    @DisplayName("빈 좋아요 집계는 모든 게시물에 0과 false를 반환한다")
    void createEmpty_anyPost_returnsZeroAndFalse() {
        // Given
        UUID postId = UUID.randomUUID();

        // When
        PostLikes postLikes = PostLikes.createEmpty();

        // Then
        assertThat(postLikes.getLikeCount(postId)).isZero();
        assertThat(postLikes.isLiked(postId)).isFalse();
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
