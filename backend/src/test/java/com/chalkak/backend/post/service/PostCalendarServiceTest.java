package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostCalendarServiceTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID APPROVED_POST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PENDING_POST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final String APPROVED_THUMBNAIL_KEY =
            "chalkak/dev/posts/thumbnail/approved.webp";
    private static final String PENDING_THUMBNAIL_KEY =
            "chalkak/dev/posts/thumbnail/pending.webp";

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @MockitoBean
    private RandomSeedGenerator randomSeedGenerator;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'calendar-service@example.com', 'ACTIVE',
                    'chalkak/dev/signatures/calendar-service.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        insertPost(
                APPROVED_POST_ID,
                LocalDate.of(2026, 8, 1),
                ModerationStatus.APPROVED,
                APPROVED_THUMBNAIL_KEY
        );
        insertPost(
                PENDING_POST_ID,
                LocalDate.of(2026, 8, 31),
                ModerationStatus.PENDING,
                PENDING_THUMBNAIL_KEY
        );
    }

    @Test
    @DisplayName("본인의 월별 게시물을 캘린더 결과로 반환한다")
    void getMyPostCalendar_validYearMonth_returnsCalendarResult() {
        // Given
        given(imageUrlProvider.getUrl(APPROVED_THUMBNAIL_KEY))
                .willReturn("https://cdn.example.com/posts/approved.webp");

        // When
        PostCalendarResult result = postQueryService.getMyPostCalendar(
                USER_ID,
                YearMonth.of(2026, 8)
        );

        // Then
        assertThat(result).isEqualTo(new PostCalendarResult(
                2026,
                8,
                List.of(
                        new PostCalendarResult.PostSummary(
                                LocalDate.of(2026, 8, 1),
                                APPROVED_POST_ID,
                                "https://cdn.example.com/posts/approved.webp",
                                ModerationStatus.APPROVED
                        )
                )
        ));
    }

    @Test
    @DisplayName("작성한 게시물이 없는 연월은 빈 캘린더 결과를 반환한다")
    void getMyPostCalendar_noPosts_returnsEmptyResult() {
        // When
        PostCalendarResult result = postQueryService.getMyPostCalendar(
                USER_ID,
                YearMonth.of(2026, 7)
        );

        // Then
        assertThat(result).isEqualTo(new PostCalendarResult(2026, 7, List.of()));
    }

    @Test
    @DisplayName("유효하지 않은 사용자의 캘린더 조회는 인증 예외를 발생시킨다")
    void getMyPostCalendar_unknownUser_throwsUnauthorizedException() {
        // Given
        UUID unknownUserId = UUID.fromString("00000000-0000-0000-0000-0000000000bf");

        // When
        UnauthorizedException exception = catchThrowableOfType(
                UnauthorizedException.class,
                () -> postQueryService.getMyPostCalendar(
                        unknownUserId,
                        YearMonth.of(2026, 8)
                )
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(exception).hasMessage("유효하지 않은 인증 정보입니다.");
    }

    private void insertPost(
            UUID postId,
            LocalDate topicDate,
            ModerationStatus moderationStatus,
            String thumbnailStorageKey
    ) {
        UUID topicId = UUID.nameUUIDFromBytes(("service-topic-" + topicDate).getBytes());
        UUID photoId = UUID.nameUUIDFromBytes(("service-photo-" + postId).getBytes());
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '캘린더 서비스 주제', ?, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP + INTERVAL '1 day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, topicId, topicDate);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                photoId,
                "chalkak/dev/posts/original/" + postId + ".webp",
                thumbnailStorageKey
        );
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?::moderation_status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, postId, USER_ID, topicId, photoId, moderationStatus.name());
    }
}
