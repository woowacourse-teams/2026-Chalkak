package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.service.post.AdminPostCommandService;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class NotificationServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572f1");
    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572a1");
    private static final UUID OTHER_USER_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6572a2");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572b1");
    private static final UUID PHOTO_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572c1");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572d1");
    private static final UUID EVENT_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572e1");
    private static final UUID SECOND_EVENT_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6572e2");
    private static final UUID NOTIFICATION_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6572f2");
    private static final UUID SECOND_NOTIFICATION_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6572f3");
    private static final Instant CREATED_AT = Instant.parse("2026-09-29T09:00:00Z");
    private static final String ORIGINAL_KEY = "chalkak/posts/inbox/original.webp";
    private static final String THUMBNAIL_KEY = "chalkak/posts/inbox/thumbnail.webp";

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private AdminPostCommandService adminPostCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @BeforeEach
    void setUp() {
        insertFixtures();
    }

    @Test
    @DisplayName("내 알림 목록에는 썸네일 URL을 포함하고 다른 회원의 알림은 제외한다")
    void getNotifications_returnsOwnNotificationsWithThumbnail() {
        // Given
        insertSecondNotification(OTHER_USER_ID);
        given(imageUrlProvider.getUrl(THUMBNAIL_KEY)).willReturn("https://cdn.test/thumbnail.webp");

        // When
        NotificationListResult result = notificationService.getNotifications(USER_ID, 1, 20);

        // Then
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().getFirst().id()).isEqualTo(NOTIFICATION_ID);
        assertThat(result.notifications().getFirst().thumbnailImageUrl())
                .isEqualTo("https://cdn.test/thumbnail.webp");
        assertThat(result.notifications().getFirst().readAt()).isNull();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("알림 목록은 최신순으로 페이지를 나누고 다음 페이지 여부를 표시한다")
    void getNotifications_ordersNewestFirstAndReturnsHasNext() {
        // Given
        insertSecondNotification(USER_ID);

        // When
        NotificationListResult first = notificationService.getNotifications(USER_ID, 1, 1);
        NotificationListResult second = notificationService.getNotifications(USER_ID, 2, 1);

        // Then
        assertThat(first.notifications()).extracting(NotificationListResult.Summary::id)
                .containsExactly(SECOND_NOTIFICATION_ID);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.notifications()).extracting(NotificationListResult.Summary::id)
                .containsExactly(NOTIFICATION_ID);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("반려 알림 상세에는 원본 사진과 사유를 포함하고 다른 회원은 조회할 수 없다")
    void getNotification_rejectedNotification_returnsOriginalAndReasonForOwnerOnly() {
        // Given
        given(imageUrlProvider.getUrl(ORIGINAL_KEY)).willReturn("https://cdn.test/original.webp");

        // When
        NotificationDetailResult detail = notificationService.getNotification(USER_ID,
                NOTIFICATION_ID);

        // Then
        assertThat(detail.originalImageUrl()).isEqualTo("https://cdn.test/original.webp");
        assertThat(detail.rejectionReason()).isEqualTo("사진 품질");
        assertThatThrownBy(
                () -> notificationService.getNotification(OTHER_USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("미읽음 여부는 로그인한 회원의 알림만 기준으로 판단한다")
    void hasUnreadNotification_returnsOwnUnreadStatus() {
        assertThat(notificationService.hasUnreadNotification(USER_ID)).isTrue();
        assertThat(notificationService.hasUnreadNotification(OTHER_USER_ID)).isFalse();
    }

    @Test
    @DisplayName("작성자가 승인 게시물을 삭제하면 알림함에서 제외하되 알림 기록은 보관한다")
    void getNotifications_authorDeletedApprovedPost_hidesNotificationButKeepsRecord() {
        // Given
        jdbcTemplate.update("""
                UPDATE posts SET moderation_status = CAST('APPROVED' AS moderation_status)
                WHERE id = ?
                """, POST_ID);
        jdbcTemplate.update("""
                UPDATE notifications
                SET type = 'POST_APPROVED', title = '게시물이 승인되었습니다.',
                    body = '내 사진이 피드에 공개되었습니다.', payload = NULL
                WHERE id = ?
                """, NOTIFICATION_ID);

        // When
        postCommandService.deletePost(USER_ID, POST_ID);

        // Then
        assertThat(notificationService.getNotifications(USER_ID, 1, 20).notifications())
                .isEmpty();
        assertThatThrownBy(() -> notificationService.getNotification(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotFoundException.class);
        assertThat(notificationService.hasUnreadNotification(USER_ID)).isFalse();
        assertThatThrownBy(() -> notificationService.markRead(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotFoundException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ?", Integer.class,
                NOTIFICATION_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자가 반려 게시물을 삭제해도 알림함에서 제외한다")
    void getNotifications_adminDeletedRejectedPost_hidesNotification() {
        // When
        adminPostCommandService.deletePost(POST_ID, ADMIN_ID, "운영 정책 위반");

        // Then
        assertThat(notificationService.getNotifications(USER_ID, 1, 20).notifications())
                .isEmpty();
        assertThatThrownBy(() -> notificationService.getNotification(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotFoundException.class);
        assertThat(notificationService.hasUnreadNotification(USER_ID)).isFalse();
        notificationService.markAllRead(USER_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND read_at IS NULL",
                Integer.class, NOTIFICATION_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("관련 게시물이 없는 알림은 조회·미읽음·읽음 처리에서 제외한다")
    void getNotifications_missingPost_hidesNotificationFromEveryInboxOperation() {
        // Given
        jdbcTemplate.update("UPDATE notifications SET source_id = ? WHERE id = ?",
                UUID.randomUUID(), NOTIFICATION_ID);

        // When & Then
        assertThat(notificationService.getNotifications(USER_ID, 1, 20).notifications())
                .isEmpty();
        assertThatThrownBy(() -> notificationService.getNotification(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotFoundException.class);
        assertThat(notificationService.hasUnreadNotification(USER_ID)).isFalse();
        assertThatThrownBy(() -> notificationService.markRead(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotFoundException.class);
        notificationService.markAllRead(USER_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND read_at IS NULL",
                Integer.class, NOTIFICATION_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("알림 읽음은 본인만 처리하며 반복 호출해도 처음 읽은 시각을 유지한다")
    void markRead_isOwnerOnlyAndIdempotent() {
        assertThatThrownBy(() -> notificationService.markRead(OTHER_USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotFoundException.class);

        notificationService.markRead(USER_ID, NOTIFICATION_ID);
        Instant firstReadAt = jdbcTemplate.queryForObject(
                "SELECT read_at FROM notifications WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(),
                NOTIFICATION_ID);
        notificationService.markRead(USER_ID, NOTIFICATION_ID);
        Instant secondReadAt = jdbcTemplate.queryForObject(
                "SELECT read_at FROM notifications WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(),
                NOTIFICATION_ID);

        assertThat(firstReadAt).isNotNull();
        assertThat(secondReadAt).isEqualTo(firstReadAt);
        assertThat(notificationService.hasUnreadNotification(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("모두 읽기는 내 미읽음 알림을 한 번에 처리한다")
    void markAllRead_marksEveryOwnUnreadNotification() {
        // Given
        insertSecondNotification(USER_ID);

        // When
        notificationService.markAllRead(USER_ID);

        // Then
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE user_id = ? AND read_at IS NULL
                """, Integer.class, USER_ID)).isZero();
    }

    private void insertSecondNotification(UUID recipientId) {
        jdbcTemplate.update("""
                INSERT INTO admin_audit_logs (
                    id, actor_admin_id, action, target_type, target_id, reason,
                    before_state, after_state, occurred_at, request_id
                ) VALUES (
                    ?, ?, CAST('POST_REJECTED' AS admin_action),
                    CAST('POST' AS admin_target_type), ?, '사진 품질',
                    CAST('{"status":"PENDING"}' AS jsonb),
                    CAST('{"status":"REJECTED"}' AS jsonb), ?, ?
                )
                """, SECOND_EVENT_ID, ADMIN_ID, POST_ID,
                Timestamp.from(CREATED_AT.plusSeconds(1)), UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO notifications (
                    id, user_id, source_type, source_id, event_key, type, title, body,
                    payload, created_at
                ) VALUES (
                    ?, ?, 'POST', ?, ?, 'POST_REJECTED', '게시물이 반려되었습니다.',
                    '반려 사유를 확인해 주세요.', CAST('{"rejectionReason":"사진 품질"}' AS jsonb), ?
                )
                """, SECOND_NOTIFICATION_ID, recipientId, POST_ID, SECOND_EVENT_ID,
                Timestamp.from(CREATED_AT.plusSeconds(1)));
    }

    private void insertFixtures() {
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'inbox-test-admin', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
        insertUser(USER_ID, "inbox-test@example.com");
        insertUser(OTHER_USER_ID, "inbox-other@example.com");
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '알림함 조회', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key, metadata,
                    created_at, updated_at
                ) VALUES (?, ?, ?, CAST('{}' AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PHOTO_ID, ORIGINAL_KEY, THUMBNAIL_KEY);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title,
                    moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, '검수 대상', CAST('REJECTED' AS moderation_status),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, POST_ID, USER_ID, TOPIC_ID, PHOTO_ID);
        jdbcTemplate.update("""
                INSERT INTO admin_audit_logs (
                    id, actor_admin_id, action, target_type, target_id, reason,
                    before_state, after_state, occurred_at, request_id
                ) VALUES (
                    ?, ?, CAST('POST_REJECTED' AS admin_action),
                    CAST('POST' AS admin_target_type), ?, '사진 품질',
                    CAST('{"status":"PENDING"}' AS jsonb),
                    CAST('{"status":"REJECTED"}' AS jsonb), ?, ?
                )
                """, EVENT_ID, ADMIN_ID, POST_ID, Timestamp.from(CREATED_AT), UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO notifications (
                    id, user_id, source_type, source_id, event_key, type, title, body,
                    payload, created_at
                ) VALUES (
                    ?, ?, 'POST', ?, ?, 'POST_REJECTED', '게시물이 반려되었습니다.',
                    '반려 사유를 확인해 주세요.', CAST('{"rejectionReason":"사진 품질"}' AS jsonb), ?
                )
                """, NOTIFICATION_ID, USER_ID, POST_ID, EVENT_ID, Timestamp.from(CREATED_AT));
    }

    private void insertUser(UUID userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, CAST('ACTIVE' AS user_status),
                    ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, userId, email, "chalkak/signatures/inbox/" + userId + "/original.webp");
    }
}
