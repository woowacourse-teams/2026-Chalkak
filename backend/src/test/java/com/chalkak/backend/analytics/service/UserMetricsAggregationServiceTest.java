package com.chalkak.backend.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.analytics.infrastructure.infra.AnalyticsProperties;
import com.chalkak.backend.analytics.infrastructure.persistence.UserMetricsRepositoryImpl;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그 지표는 시작일을 먼 미래로 두어 끄고 DB 지표만 검증한다. 로그 지표는
 * {@link UserMetricsAggregationLogMetricsTest}에서 다룬다.
 *
 * <p>
 * 게시물 지표는 2030년 날짜로 한정해 다른 테스트가 남긴 행과 섞이지 않게 한다. 회원·좋아요 지표는 DB 전체의 스냅샷이라
 * 데이터를 넣기 전후의 차이로 검증한다.
 */
@Transactional
class UserMetricsAggregationServiceTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate METRIC_DATE = LocalDate.of(2030, 3, 15);
    private static final LocalDate SUNDAY_METRIC_DATE = LocalDate.of(2030, 3, 17);
    private static final String LOG_GROUP = "/chalkak/test/application";
    private static final LocalDate LOG_METRICS_START_DATE = LocalDate.of(2099, 1, 1);
    private static final LocalDate TOPIC_BASE_DATE = LocalDate.of(2041, 1, 1);

    @Autowired
    private UserMetricsAggregationService aggregationService;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Logger aggregationLogger = (Logger) LoggerFactory.getLogger("chalkak.aggregation");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private int sequence;

    @BeforeEach
    void attachAppender() {
        appender.start();
        aggregationLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        aggregationLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("KST 하루의 시작은 포함하고 직전과 다음 날 0시는 제외한다")
    void aggregate_dayBoundary_countsOnlyPostsInsideKstDay() {
        // Given
        UUID insideStart = insertUser("boundary-start");
        UUID insideEnd = insertUser("boundary-end");
        UUID before = insertUser("boundary-before");
        UUID after = insertUser("boundary-after");
        insertPost(insideStart, kst(METRIC_DATE, "00:00:00"), "APPROVED", false);
        insertPost(insideEnd, kst(METRIC_DATE, "23:59:59"), "APPROVED", false);
        insertPost(before, kst(METRIC_DATE.minusDays(1), "23:59:59"), "APPROVED", false);
        insertPost(after, kst(METRIC_DATE.plusDays(1), "00:00:00"), "APPROVED", false);

        // When
        DailyUserMetrics metrics = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(metrics.dau()).isEqualTo(2);
        assertThat(metrics.posts()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 회원이 같은 날 올린 게시물은 dau에 한 번만 세고 posts에는 모두 센다")
    void aggregate_sameUserTwoPosts_countsDauOnce() {
        // Given
        UUID userId = insertUser("twice");
        insertPost(userId, kst(METRIC_DATE, "09:00:00"), "APPROVED", false);
        insertPost(userId, kst(METRIC_DATE, "21:00:00"), "REJECTED", false);

        // When
        DailyUserMetrics metrics = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(metrics.dau()).isEqualTo(1);
        assertThat(metrics.posts()).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제됐거나 거절된 게시물도 업로드로 보고 dau와 posts에 센다")
    void aggregate_deletedAndRejectedPosts_areCounted() {
        // Given
        UUID deletedAuthor = insertUser("deleted-post");
        UUID rejectedAuthor = insertUser("rejected-post");
        insertPost(deletedAuthor, kst(METRIC_DATE, "10:00:00"), "APPROVED", true);
        insertPost(rejectedAuthor, kst(METRIC_DATE, "11:00:00"), "REJECTED", false);

        // When
        DailyUserMetrics metrics = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(metrics.dau()).isEqualTo(2);
        assertThat(metrics.posts()).isEqualTo(2);
    }

    @Test
    @DisplayName("wau는 기준일 6일 전 0시부터 기준일 끝까지 7일을 센다")
    void aggregate_weekWindow_coversSixDaysBeforeThroughMetricDate() {
        // Given
        UUID beforeWindow = insertUser("week-before");
        UUID firstDay = insertUser("week-first");
        UUID middle = insertUser("week-middle");
        UUID lastDay = insertUser("week-last");
        UUID afterWindow = insertUser("week-after");
        insertPost(beforeWindow, kst(METRIC_DATE.minusDays(7), "23:59:59"), "APPROVED", false);
        insertPost(firstDay, kst(METRIC_DATE.minusDays(6), "00:00:00"), "APPROVED", false);
        insertPost(middle, kst(METRIC_DATE.minusDays(3), "12:00:00"), "APPROVED", false);
        insertPost(lastDay, kst(METRIC_DATE, "23:59:59"), "APPROVED", false);
        insertPost(afterWindow, kst(METRIC_DATE.plusDays(1), "00:00:00"), "APPROVED", false);

        // When
        DailyUserMetrics metrics = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(metrics.wau()).isEqualTo(3);
        assertThat(metrics.dau()).isEqualTo(1);
    }

    @Test
    @DisplayName("제외 회원은 dau, posts, wau, eligibleUsers, likesTotal에서 모두 빠진다")
    void aggregate_excludedUser_isRemovedFromEveryMetric() {
        // Given
        UUID member = insertUser("excluded-member");
        UUID excluded = insertUser("excluded-team");
        UUID memberPost = insertPost(member, kst(METRIC_DATE, "10:00:00"), "APPROVED", false);
        insertPost(excluded, kst(METRIC_DATE, "10:00:00"), "APPROVED", false);
        insertLike(memberPost, member);
        insertLike(memberPost, excluded);
        UserMetricsAggregationService withoutExclusion = serviceExcluding(List.of());
        UserMetricsAggregationService withExclusion = serviceExcluding(List.of(excluded));
        DailyUserMetrics all = withoutExclusion.aggregate(METRIC_DATE);

        // When
        DailyUserMetrics filtered = withExclusion.aggregate(METRIC_DATE);

        // Then
        assertThat(all.dau() - filtered.dau()).isEqualTo(1);
        assertThat(all.posts() - filtered.posts()).isEqualTo(1);
        assertThat(all.wau() - filtered.wau()).isEqualTo(1);
        assertThat(all.eligibleUsers() - filtered.eligibleUsers()).isEqualTo(1);
        assertThat(all.likesTotal() - filtered.likesTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("eligibleUsers는 정지된 회원과 탈퇴한 회원을 센 결과에서 뺀다")
    void aggregate_bannedAndWithdrawnUsers_areNotEligible() {
        // Given
        long before = aggregationService.aggregate(METRIC_DATE).eligibleUsers();
        insertUser("eligible-active");
        UUID banned = insertUser("eligible-banned");
        UUID withdrawn = insertUser("eligible-withdrawn");
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", banned);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", withdrawn);

        // When
        DailyUserMetrics metrics = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(metrics.eligibleUsers() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("likesTotal은 삭제된 게시물에 달린 좋아요를 세지 않는다")
    void aggregate_likesOnDeletedPost_areNotCounted() {
        // Given
        long before = aggregationService.aggregate(METRIC_DATE).likesTotal();
        UUID author = insertUser("like-author");
        UUID liker = insertUser("like-liker");
        UUID livePost = insertPost(author, kst(METRIC_DATE, "10:00:00"), "APPROVED", false);
        UUID deletedPost = insertPost(author, kst(METRIC_DATE, "11:00:00"), "APPROVED", true);
        insertLike(livePost, liker);
        insertLike(deletedPost, liker);

        // When
        DailyUserMetrics metrics = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(metrics.likesTotal() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("집계 결과를 chalkak.aggregation 로그 한 줄에 type과 metricDate와 지표 값으로 남긴다")
    void aggregate_success_logsOneAggregationLine() {
        // Given
        UUID userId = insertUser("log-line");
        UUID postId = insertPost(userId, kst(METRIC_DATE, "10:00:00"), "APPROVED", false);
        insertLike(postId, userId);
        appender.list.clear();

        // When
        DailyUserMetrics metrics = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getFormattedMessage()).isEqualTo("사용자 지표 집계");
        assertThat(keyValue(event, "type")).isEqualTo("aggregation");
        assertThat(keyValue(event, "metricDate")).isEqualTo("2030-03-15");
        assertThat(keyValue(event, "dau")).isEqualTo(metrics.dau()).isEqualTo(1L);
        assertThat(keyValue(event, "posts")).isEqualTo(metrics.posts()).isEqualTo(1L);
        assertThat(keyValue(event, "wau")).isEqualTo(metrics.wau()).isEqualTo(1L);
        assertThat(keyValue(event, "eligibleUsers")).isEqualTo(metrics.eligibleUsers());
        assertThat(keyValue(event, "likesTotal")).isEqualTo(metrics.likesTotal());
    }

    @Test
    @DisplayName("같은 날짜를 두 번 집계하면 게시물 기반 지표가 같다")
    void aggregate_sameDateTwice_returnsSameDbDerivedValues() {
        // Given
        UUID userId = insertUser("idempotent");
        insertPost(userId, kst(METRIC_DATE, "10:00:00"), "APPROVED", false);

        // When
        DailyUserMetrics first = aggregationService.aggregate(METRIC_DATE);
        DailyUserMetrics second = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("일요일 기준일은 1~4주 전 코호트별 인원과 유지 회원 수를 주 경계에 맞춰 센다")
    void aggregate_sunday_countsWeeklyRetentionPerCohort() {
        // Given: 기준 주 2030-03-11(월)~03-17(일), 코호트 W+1=03-04, W+2=02-25, W+3=02-18, W+4=02-11
        UUID returned = insertUser("retention-returned");
        UUID cohortOnly = insertUser("retention-cohort-only");
        UUID mondayEdge = insertUser("retention-monday-edge");
        UUID returnsTooLate = insertUser("retention-too-late");
        UUID deletedAuthor = insertUser("retention-deleted");
        UUID farCohort = insertUser("retention-far");
        UUID targetOnly = insertUser("retention-target-only");
        UUID previousSunday = insertUser("retention-previous-sunday");
        UUID excluded = insertUser("retention-excluded");
        insertPost(returned, kst(LocalDate.of(2030, 3, 5), "10:00:00"), "APPROVED", false);
        insertPost(returned, kst(LocalDate.of(2030, 3, 1), "10:00:00"), "APPROVED", false);
        insertPost(returned, kst(LocalDate.of(2030, 3, 12), "10:00:00"), "APPROVED", false);
        insertPost(cohortOnly, kst(LocalDate.of(2030, 3, 6), "10:00:00"), "APPROVED", false);
        insertPost(mondayEdge, kst(LocalDate.of(2030, 3, 4), "00:00:00"), "APPROVED", false);
        insertPost(mondayEdge, kst(LocalDate.of(2030, 3, 11), "00:00:00"), "APPROVED", false);
        insertPost(returnsTooLate, kst(LocalDate.of(2030, 3, 10), "23:59:59"), "APPROVED", false);
        insertPost(returnsTooLate, kst(LocalDate.of(2030, 3, 18), "00:00:00"), "APPROVED", false);
        insertPost(deletedAuthor, kst(LocalDate.of(2030, 2, 20), "10:00:00"), "APPROVED", true);
        insertPost(deletedAuthor, kst(LocalDate.of(2030, 3, 14), "10:00:00"), "REJECTED", false);
        insertPost(farCohort, kst(LocalDate.of(2030, 2, 12), "10:00:00"), "APPROVED", false);
        insertPost(farCohort, kst(LocalDate.of(2030, 3, 13), "10:00:00"), "APPROVED", false);
        insertPost(targetOnly, kst(LocalDate.of(2030, 3, 11), "00:00:00"), "APPROVED", false);
        insertPost(previousSunday, kst(LocalDate.of(2030, 3, 3), "23:59:59"), "APPROVED", false);
        insertPost(excluded, kst(LocalDate.of(2030, 3, 5), "10:00:00"), "APPROVED", false);
        insertPost(excluded, kst(LocalDate.of(2030, 3, 12), "10:00:00"), "APPROVED", false);

        // When
        DailyUserMetrics metrics = serviceExcluding(List.of(excluded)).aggregate(SUNDAY_METRIC_DATE);

        // Then
        assertThat(metrics.weeklyRetentions()).containsExactly(
                new WeeklyRetention(1, LocalDate.of(2030, 3, 4), 4, 2),
                new WeeklyRetention(2, LocalDate.of(2030, 2, 25), 2, 1),
                new WeeklyRetention(3, LocalDate.of(2030, 2, 18), 1, 1),
                new WeeklyRetention(4, LocalDate.of(2030, 2, 11), 1, 1));
    }

    @Test
    @DisplayName("제외 회원이 없으면 제외 회원도 코호트와 유지 회원 수에 센다")
    void aggregate_sundayWithoutExclusion_countsEveryUser() {
        // Given
        UUID member = insertUser("retention-member");
        insertPost(member, kst(LocalDate.of(2030, 3, 5), "10:00:00"), "APPROVED", false);
        insertPost(member, kst(LocalDate.of(2030, 3, 12), "10:00:00"), "APPROVED", false);

        // When
        DailyUserMetrics metrics = serviceExcluding(List.of()).aggregate(SUNDAY_METRIC_DATE);

        // Then
        assertThat(metrics.weeklyRetentions().getFirst())
                .isEqualTo(new WeeklyRetention(1, LocalDate.of(2030, 3, 4), 1, 1));
    }

    @Test
    @DisplayName("일요일이 아닌 기준일은 유지율을 계산하지 않고 로그에도 남기지 않는다")
    void aggregate_nonSunday_hasNoRetention() {
        // Given
        appender.list.clear();

        // When
        DailyUserMetrics metrics = aggregationService.aggregate(METRIC_DATE);

        // Then
        assertThat(metrics.weeklyRetentions()).isEmpty();
        assertThat(appender.list.getFirst().getKeyValuePairs())
                .noneMatch(pair -> pair.key.startsWith("retention"));
    }

    private UserMetricsAggregationService serviceExcluding(List<UUID> excludedUserIds) {
        AnalyticsProperties properties =
                new AnalyticsProperties(excludedUserIds, LOG_GROUP, LOG_METRICS_START_DATE);
        AccessLogMetricsReader reader = mock(AccessLogMetricsReader.class);
        when(reader.read(any())).thenReturn(Optional.empty());
        return new UserMetricsAggregationService(
                new UserMetricsRepositoryImpl(namedParameterJdbcTemplate, properties),
                reader);
    }

    private OffsetDateTime kst(LocalDate date, String time) {
        return LocalTime.parse(time).atDate(date).atZone(KST).toOffsetDateTime();
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst()
                .orElseThrow()
                .value;
    }

    private UUID insertUser(String slug) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                userId,
                slug + "-" + userId + "@example.com",
                "chalkak/dev/signatures/" + userId + ".png");
        return userId;
    }

    /** 게시물마다 새 주제를 만든다. 주제의 참여 기간은 서로 겹치지 못하므로 날짜를 하루씩 밀어 쓴다. */
    private UUID insertPost(
            UUID authorId,
            OffsetDateTime createdAt,
            String moderationStatus,
            boolean deleted
    ) {
        UUID topicId = insertTopic();
        UUID photoId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, photoId, "chalkak/dev/posts/original/" + photoId + ".webp");
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, ?, ?::moderation_status,
                    ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP END
                )
                """,
                postId,
                authorId,
                topicId,
                photoId,
                moderationStatus,
                createdAt,
                createdAt,
                deleted);
        return postId;
    }

    private UUID insertTopic() {
        UUID topicId = UUID.randomUUID();
        LocalDate topicDate = TOPIC_BASE_DATE.plusDays(sequence++);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '지표 집계 주제', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                topicId,
                topicDate,
                topicDate.atStartOfDay(KST).toOffsetDateTime(),
                topicDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime());
        return topicId;
    }

    private void insertLike(UUID postId, UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                postId,
                userId);
    }
}
