package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostTodayStatusServiceTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID POST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final String NOT_TIMED_OUT = "1 minute";
    private static final String TIMED_OUT = "8 minutes";

    /** 지금을 포함하는 참여 기간. 이 주제만 조회 대상이 된다. */
    private static final String OPEN = "-1 hour, +1 hour";
    /** 아직 시작하지 않은 참여 기간. */
    private static final String BEFORE_OPEN = "+1 hour, +2 hours";
    /** 이미 끝난 참여 기간. */
    private static final String CLOSED = "-2 hours, -1 hour";

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
        insertUser(USER_ID, "today-status");
        insertUser(OTHER_USER_ID, "today-status-other");
    }

    @ParameterizedTest
    @EnumSource(value = ModerationStatus.class, names = {"VALIDATING", "PENDING", "APPROVED"})
    @DisplayName("참여할 수 있는 주제에 재작성을 막는 게시물이 있으면 작성함으로 판정한다")
    void getMyTodayPostStatus_activePostExists_returnsPosted(ModerationStatus moderationStatus) {
        // Given
        LocalDate topicDate = LocalDate.now(KST);
        insertTopic(topicDate, OPEN);
        insertPost(USER_ID, topicDate, moderationStatus, NOT_TIMED_OUT);

        // When
        TodayPostStatus status = postQueryService.getMyTodayPostStatus(USER_ID);

        // Then
        assertThat(status).isEqualTo(new TodayPostStatus(
                topicDate,
                true,
                POST_ID,
                moderationStatus
        ));
    }

    @Test
    @DisplayName("이미지 처리 대기 시간을 넘긴 게시물은 재작성할 수 있으므로 미작성으로 판정한다")
    void getMyTodayPostStatus_validatingPostTimedOut_returnsNotPosted() {
        // Given
        LocalDate topicDate = LocalDate.now(KST);
        insertTopic(topicDate, OPEN);
        insertPost(USER_ID, topicDate, ModerationStatus.VALIDATING, TIMED_OUT);

        // When
        TodayPostStatus status = postQueryService.getMyTodayPostStatus(USER_ID);

        // Then
        assertThat(status).isEqualTo(TodayPostStatus.notPosted(topicDate));
    }

    @Test
    @DisplayName("거절된 게시물만 있으면 미작성으로 판정한다")
    void getMyTodayPostStatus_rejectedPost_returnsNotPosted() {
        // Given
        LocalDate topicDate = LocalDate.now(KST);
        insertTopic(topicDate, OPEN);
        insertPost(USER_ID, topicDate, ModerationStatus.REJECTED, NOT_TIMED_OUT);

        // When
        TodayPostStatus status = postQueryService.getMyTodayPostStatus(USER_ID);

        // Then
        assertThat(status).isEqualTo(TodayPostStatus.notPosted(topicDate));
    }

    @Test
    @DisplayName("삭제한 게시물만 있으면 미작성으로 판정한다")
    void getMyTodayPostStatus_deletedPost_returnsNotPosted() {
        // Given
        LocalDate topicDate = LocalDate.now(KST);
        insertTopic(topicDate, OPEN);
        insertPost(USER_ID, topicDate, ModerationStatus.APPROVED, NOT_TIMED_OUT);
        jdbcTemplate.update(
                "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                POST_ID
        );

        // When
        TodayPostStatus status = postQueryService.getMyTodayPostStatus(USER_ID);

        // Then
        assertThat(status).isEqualTo(TodayPostStatus.notPosted(topicDate));
    }

    @Test
    @DisplayName("다른 회원이 같은 주제에 작성한 게시물은 판정에 영향을 주지 않는다")
    void getMyTodayPostStatus_otherAuthorPost_returnsNotPosted() {
        // Given
        LocalDate topicDate = LocalDate.now(KST);
        insertTopic(topicDate, OPEN);
        insertPost(OTHER_USER_ID, topicDate, ModerationStatus.APPROVED, NOT_TIMED_OUT);

        // When
        TodayPostStatus status = postQueryService.getMyTodayPostStatus(USER_ID);

        // Then
        assertThat(status).isEqualTo(TodayPostStatus.notPosted(topicDate));
    }

    @Test
    @DisplayName("참여 기간이 지난 주제에 작성했어도 열린 주제 기준으로 판정한다")
    void getMyTodayPostStatus_postOnClosedTopic_returnsNotPosted() {
        // Given
        LocalDate openTopicDate = LocalDate.now(KST);
        LocalDate closedTopicDate = openTopicDate.minusDays(1);
        insertTopic(openTopicDate, OPEN);
        insertTopic(closedTopicDate, CLOSED);
        insertPost(USER_ID, closedTopicDate, ModerationStatus.APPROVED, NOT_TIMED_OUT);

        // When
        TodayPostStatus status = postQueryService.getMyTodayPostStatus(USER_ID);

        // Then
        assertThat(status).isEqualTo(TodayPostStatus.notPosted(openTopicDate));
    }

    @Test
    @DisplayName("참여 기간이 자정을 넘긴 주제는 어제 날짜로 응답한다")
    void getMyTodayPostStatus_topicOpenedYesterday_returnsYesterdayTopicDate() {
        // Given
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        insertTopic(yesterday, OPEN);

        // When
        TodayPostStatus status = postQueryService.getMyTodayPostStatus(USER_ID);

        // Then
        assertThat(status).isEqualTo(TodayPostStatus.notPosted(yesterday));
    }

    @Test
    @DisplayName("아직 시작하지 않은 주제뿐이면 참여할 주제가 없다고 응답한다")
    void getMyTodayPostStatus_beforeOpenTopicOnly_throwsNotFoundException() {
        // Given
        insertTopic(LocalDate.now(KST), BEFORE_OPEN);

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postQueryService.getMyTodayPostStatus(USER_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("참여할 수 있는 주제가 없습니다.");
    }

    @Test
    @DisplayName("이미 끝난 주제뿐이면 참여할 주제가 없다고 응답한다")
    void getMyTodayPostStatus_closedTopicOnly_throwsNotFoundException() {
        // Given
        insertTopic(LocalDate.now(KST), CLOSED);

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postQueryService.getMyTodayPostStatus(USER_ID)
        );

        // Then
        assertThat(exception).hasMessage("참여할 수 있는 주제가 없습니다.");
    }

    @Test
    @DisplayName("주제가 하나도 없으면 참여할 주제가 없다고 응답한다")
    void getMyTodayPostStatus_noTopic_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postQueryService.getMyTodayPostStatus(USER_ID)
        );

        // Then
        assertThat(exception).hasMessage("참여할 수 있는 주제가 없습니다.");
    }

    @Test
    @DisplayName("열린 주제가 삭제되면 참여할 주제가 없다고 응답한다")
    void getMyTodayPostStatus_deletedOpenTopic_throwsNotFoundException() {
        // Given
        LocalDate topicDate = LocalDate.now(KST);
        insertTopic(topicDate, OPEN);
        jdbcTemplate.update(
                "UPDATE topics SET deleted_at = CURRENT_TIMESTAMP WHERE topic_date = ?",
                topicDate
        );

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postQueryService.getMyTodayPostStatus(USER_ID)
        );

        // Then
        assertThat(exception).hasMessage("참여할 수 있는 주제가 없습니다.");
    }

    @Test
    @DisplayName("유효하지 않은 사용자의 조회는 인증 예외를 발생시킨다")
    void getMyTodayPostStatus_unknownUser_throwsUnauthorizedException() {
        // Given
        insertTopic(LocalDate.now(KST), OPEN);
        UUID unknownUserId = UUID.fromString("00000000-0000-0000-0000-0000000000cf");

        // When
        UnauthorizedException exception = catchThrowableOfType(
                UnauthorizedException.class,
                () -> postQueryService.getMyTodayPostStatus(unknownUserId)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(exception).hasMessage("유효하지 않은 인증 정보입니다.");
    }

    private void insertUser(UUID userId, String slug) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                userId,
                slug + "@example.com",
                "chalkak/dev/signatures/" + slug + ".png"
        );
    }

    private void insertTopic(LocalDate topicDate, String participationPeriod) {
        String[] offsets = participationPeriod.split(", ");
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '오늘 작성 여부 주제', ?,
                    CURRENT_TIMESTAMP + CAST(? AS INTERVAL),
                    CURRENT_TIMESTAMP + CAST(? AS INTERVAL),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, topicId(topicDate), topicDate, offsets[0], offsets[1]);
    }

    private void insertPost(
            UUID authorId,
            LocalDate topicDate,
            ModerationStatus moderationStatus,
            String createdBefore
    ) {
        UUID photoId = UUID.nameUUIDFromBytes(("today-photo-" + authorId + topicDate).getBytes());
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, photoId, "chalkak/dev/posts/original/" + photoId + ".webp");
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?::moderation_status,
                    CURRENT_TIMESTAMP - CAST(? AS INTERVAL), CURRENT_TIMESTAMP
                )
                """,
                POST_ID,
                authorId,
                topicId(topicDate),
                photoId,
                moderationStatus.name(),
                createdBefore
        );
    }

    private UUID topicId(LocalDate topicDate) {
        return UUID.nameUUIDFromBytes(("today-topic-" + topicDate).getBytes());
    }
}
