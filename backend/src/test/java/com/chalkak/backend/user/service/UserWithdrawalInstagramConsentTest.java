package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.support.AuthorPostFixtures;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserWithdrawalInstagramConsentTest extends IntegrationTestSupport {

    private static final Instant AGREED_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant PREVIOUS_REVOKED_AT = Instant.parse("2026-11-02T00:00:00Z");

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("탈퇴하면 동의한 게시물의 동의를 철회하고 ID를 지우며 철회 시각을 탈퇴 시각으로 기록한다")
    void withdraw_agreedPost_revokesConsentAtWithdrawalTime() {
        // Given
        User user = saveUser();
        UUID postId = createPost(
                user,
                LocalDate.parse("2026-11-01"),
                true,
                "chalkak"
        );
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        Map<String, Object> post = readPost(postId);
        assertThat(post.get("instagram_upload_agreed")).isEqualTo(false);
        assertThat(post.get("instagram_id")).isNull();
        assertThat(post.get("instagram_agreed_at")).isEqualTo(Timestamp.from(AGREED_AT));
        assertThat(post.get("instagram_revoked_at")).isEqualTo(readWithdrawnAt(user));
    }

    @Test
    @DisplayName("동의하지 않은 게시물은 이전 철회 시각까지 그대로 둔다")
    void withdraw_notAgreedPost_keepsPostUnchanged() {
        // Given
        User user = saveUser();
        UUID neverAgreedPostId = createPost(
                user,
                LocalDate.parse("2026-11-01"),
                false,
                null
        );
        UUID revokedPostId = createPost(
                user,
                LocalDate.parse("2026-11-02"),
                false,
                null
        );
        jdbcTemplate.update(
                "UPDATE posts SET instagram_agreed_at = ?, instagram_revoked_at = ? WHERE id = ?",
                Timestamp.from(AGREED_AT),
                Timestamp.from(PREVIOUS_REVOKED_AT),
                revokedPostId
        );
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        assertThat(readPost(neverAgreedPostId).get("instagram_revoked_at")).isNull();
        assertThat(readPost(revokedPostId).get("instagram_revoked_at"))
                .isEqualTo(Timestamp.from(PREVIOUS_REVOKED_AT));
    }

    @Test
    @DisplayName("삭제된 게시물의 동의도 철회하고 게시물은 삭제된 채로 남긴다")
    void withdraw_deletedAgreedPost_revokesConsentAndKeepsDeletion() {
        // Given
        User user = saveUser();
        UUID postId = createPost(
                user,
                LocalDate.parse("2026-11-01"),
                true,
                null
        );
        jdbcTemplate.update(
                "UPDATE posts SET deleted_at = ? WHERE id = ?",
                Timestamp.from(PREVIOUS_REVOKED_AT),
                postId
        );
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        Map<String, Object> post = readPost(postId);
        assertThat(post.get("instagram_upload_agreed")).isEqualTo(false);
        assertThat(post.get("instagram_revoked_at")).isEqualTo(readWithdrawnAt(user));
        assertThat(post.get("deleted_at")).isEqualTo(Timestamp.from(PREVIOUS_REVOKED_AT));
    }

    @Test
    @DisplayName("다른 회원의 게시물 동의는 바꾸지 않고 탈퇴한 회원의 게시물도 삭제하지 않는다")
    void withdraw_otherMemberPost_keepsOtherConsentAndDoesNotDeletePosts() {
        // Given
        User user = saveUser();
        User otherUser = saveUser();
        UUID postId = createPost(
                user,
                LocalDate.parse("2026-11-01"),
                true,
                "chalkak"
        );
        UUID otherPostId = createPost(
                otherUser,
                LocalDate.parse("2026-11-02"),
                true,
                "other"
        );
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        Map<String, Object> other = readPost(otherPostId);
        assertThat(other.get("instagram_upload_agreed")).isEqualTo(true);
        assertThat(other.get("instagram_id")).isEqualTo("other");
        assertThat(other.get("instagram_revoked_at")).isNull();
        assertThat(readPost(postId).get("deleted_at")).isNull();
    }

    @Test
    @DisplayName("정지 회원이 탈퇴해도 게시물의 동의를 철회한다")
    void withdraw_bannedUser_revokesConsent() {
        // Given
        User user = userRepository.save(UserFixture.createBanned(null));
        UUID postId = createPost(
                user,
                LocalDate.parse("2026-11-01"),
                true,
                "chalkak"
        );
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        Map<String, Object> post = readPost(postId);
        assertThat(post.get("instagram_upload_agreed")).isEqualTo(false);
        assertThat(post.get("instagram_id")).isNull();
        assertThat(post.get("instagram_revoked_at")).isEqualTo(readWithdrawnAt(user));
    }

    private User saveUser() {
        return userRepository.save(UserFixture.create());
    }

    private UUID createPost(
            User user,
            LocalDate topicDate,
            boolean agreed,
            String instagramId
    ) {
        entityManager.flush();
        UUID postId = AuthorPostFixtures.createPost(
                jdbcTemplate,
                user.getId(),
                topicDate,
                ModerationStatus.APPROVED
        );
        jdbcTemplate.update(
                """
                        UPDATE posts
                        SET instagram_upload_agreed = ?, instagram_id = ?, instagram_agreed_at = ?
                        WHERE id = ?
                        """,
                agreed,
                instagramId,
                agreed ? Timestamp.from(AGREED_AT) : null,
                postId
        );
        return postId;
    }

    private Map<String, Object> readPost(UUID postId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM posts WHERE id = ?",
                postId
        );
        return rows.getFirst();
    }

    private Object readWithdrawnAt(User user) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM users WHERE id = ?",
                Timestamp.class,
                user.getId()
        );
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
