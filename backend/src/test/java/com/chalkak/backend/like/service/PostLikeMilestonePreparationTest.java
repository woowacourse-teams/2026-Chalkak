package com.chalkak.backend.like.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.support.AuthorPostFixtures;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = "chalkak.notification.like-milestone.enabled=false")
class PostLikeMilestonePreparationTest extends IntegrationTestSupport {

    @Autowired
    private PostLikeService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("1차 OFF 배포에서는 좋아요와 최고 단계를 저장하고 알림은 만들지 않는다")
    void likePost_preparation_persistsCountAndBaselineWithoutNotification() {
        // Given
        UUID authorId = AuthorPostFixtures.createUser(jdbc);
        UUID postId = AuthorPostFixtures.createPost(
                jdbc,
                authorId,
                LocalDate.parse("2026-11-03"),
                ModerationStatus.APPROVED
        );

        // When
        for (int index = 0; index < 6; index++) {
            UUID likerId = AuthorPostFixtures.createUser(jdbc);
            service.likePost(postId, likerId);
        }

        // Then
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Long.class,
                postId
        )).isEqualTo(6L);
        assertThat(jdbc.queryForObject(
                "SELECT like_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                postId
        )).isEqualTo(6L);
        assertThat(jdbc.queryForObject(
                "SELECT last_milestone_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                postId
        )).isEqualTo(5L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE source_id = ?",
                Long.class,
                postId
        )).isZero();
    }
}
