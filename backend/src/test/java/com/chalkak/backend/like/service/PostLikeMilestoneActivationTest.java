package com.chalkak.backend.like.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.support.AuthorPostFixtures;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = {
        "NOTIFICATION_LIKE_MILESTONE_ENABLED=false",
        "chalkak.notification.like-milestone.enabled=false"
})
class PostLikeMilestoneActivationTest extends IntegrationTestSupport {

    @Autowired
    private PostLikeService service;

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("1차 OFF 설정이 남아 있어도 알림을 생성하고 회원 설정에 따라 푸시 발행을 결정한다")
    void likePost_legacyDisabledSetting_createsNotification(boolean pushEnabled) {
        // Given
        UUID authorId = AuthorPostFixtures.createUser(jdbc);
        jdbc.update(
                "UPDATE users SET like_push_enabled = ? WHERE id = ?",
                pushEnabled,
                authorId
        );
        UUID postId = AuthorPostFixtures.createPost(
                jdbc,
                authorId,
                LocalDate.parse("2026-11-03"),
                ModerationStatus.APPROVED
        );

        // When
        for (int index = 0; index < 6; index++) {
            service.likePost(postId, AuthorPostFixtures.createUser(jdbc));
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
                "SELECT COUNT(*) FROM notifications WHERE source_id = ? AND user_id = ? AND type = 'POST_LIKE_MILESTONE'",
                Long.class,
                postId,
                authorId
        )).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT sqs_publish_status FROM notifications WHERE source_id = ?",
                String.class,
                postId
        )).isEqualTo(pushEnabled ? "PENDING" : "NOT_REQUIRED");
    }
}
