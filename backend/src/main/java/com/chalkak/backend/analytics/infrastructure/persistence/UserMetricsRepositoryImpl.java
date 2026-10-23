package com.chalkak.backend.analytics.infrastructure.persistence;

import com.chalkak.backend.analytics.infrastructure.infra.AnalyticsProperties;
import com.chalkak.backend.analytics.repository.UserMetricsRepository;
import com.chalkak.backend.analytics.repository.AiReviewCounts;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 여러 테이블을 가로지르는 집계라 엔티티에 묶지 않고 네이티브 SQL로 센다.
 *
 * <p>
 * 제외할 회원 ID는 {@code uuid[]} 파라미터 하나로 넘긴다. {@code NOT IN ()}은 목록이 비면 문법 오류라, 빈
 * 배열에도 참이 되는 {@code <> ALL(배열)}을 쓴다.
 */
@Repository
public class UserMetricsRepositoryImpl implements UserMetricsRepository {

    private static final String COUNT_POST_AUTHORS = """
            SELECT COUNT(DISTINCT p.user_id)
            FROM posts p
            WHERE p.created_at >= :from
              AND p.created_at < :to
              AND p.user_id <> ALL (CAST(:excludedUserIds AS uuid[]))
            """;

    private static final String COUNT_POSTS = """
            SELECT COUNT(*)
            FROM posts p
            WHERE p.created_at >= :from
              AND p.created_at < :to
              AND p.user_id <> ALL (CAST(:excludedUserIds AS uuid[]))
            """;

    private static final String COUNT_RETURNING_POST_AUTHORS = """
            SELECT COUNT(DISTINCT c.user_id)
            FROM posts c
            WHERE c.created_at >= :cohortFrom
              AND c.created_at < :cohortTo
              AND c.user_id <> ALL (CAST(:excludedUserIds AS uuid[]))
              AND EXISTS (
                SELECT 1
                FROM posts r
                WHERE r.user_id = c.user_id
                  AND r.created_at >= :returnFrom
                  AND r.created_at < :returnTo
              )
            """;

    /**
     * 가입 때 사인 등록이 끝나야 하므로({@code signature_original_storage_key}는 NOT NULL) 탈퇴하지 않은
     * ACTIVE 회원은 모두 사인을 등록한 회원이다.
     */
    private static final String COUNT_ELIGIBLE_USERS = """
            SELECT COUNT(*)
            FROM users u
            WHERE u.deleted_at IS NULL
              AND u.status = 'ACTIVE'
              AND u.id <> ALL (CAST(:excludedUserIds AS uuid[]))
            """;

    private static final String COUNT_LIKES_ON_LIVE_POSTS = """
            SELECT COUNT(*)
            FROM post_likes pl
            JOIN posts p ON p.id = pl.post_id
            WHERE p.deleted_at IS NULL
              AND pl.user_id <> ALL (CAST(:excludedUserIds AS uuid[]))
            """;

    /** 재요청은 {@code requested_at}을 바꾸지 않으므로 최초 요청만 센다. 완료·실패는 집계 시점 상태다. */
    private static final String COUNT_AI_REVIEWS = """
            SELECT COUNT(DISTINCT r.user_id)                      AS users,
                   COUNT(*)                                       AS requests,
                   COUNT(*) FILTER (WHERE r.status = 'COMPLETED') AS completed,
                   COUNT(*) FILTER (WHERE r.status = 'FAILED')    AS failed
            FROM ai_reviews r
            WHERE r.requested_at >= :from
              AND r.requested_at < :to
              AND r.user_id <> ALL (CAST(:excludedUserIds AS uuid[]))
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UUID[] excludedUserIds;

    public UserMetricsRepositoryImpl(
            NamedParameterJdbcTemplate jdbcTemplate,
            AnalyticsProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.excludedUserIds = properties.excludedUserIds().toArray(UUID[]::new);
    }

    @Override
    public long countPostAuthors(
            Instant from,
            Instant to
    ) {
        return count(COUNT_POST_AUTHORS, periodParameters(from, to));
    }

    @Override
    public long countPosts(
            Instant from,
            Instant to
    ) {
        return count(COUNT_POSTS, periodParameters(from, to));
    }

    @Override
    public long countReturningPostAuthors(
            Instant cohortFrom,
            Instant cohortTo,
            Instant returnFrom,
            Instant returnTo
    ) {
        return count(COUNT_RETURNING_POST_AUTHORS, parameters()
                .addValue("cohortFrom", OffsetDateTime.ofInstant(cohortFrom, ZoneOffset.UTC))
                .addValue("cohortTo", OffsetDateTime.ofInstant(cohortTo, ZoneOffset.UTC))
                .addValue("returnFrom", OffsetDateTime.ofInstant(returnFrom, ZoneOffset.UTC))
                .addValue("returnTo", OffsetDateTime.ofInstant(returnTo, ZoneOffset.UTC)));
    }

    @Override
    public long countEligibleUsers() {
        return count(COUNT_ELIGIBLE_USERS, parameters());
    }

    @Override
    public long countLikesOnLivePosts() {
        return count(COUNT_LIKES_ON_LIVE_POSTS, parameters());
    }

    @Override
    public AiReviewCounts countAiReviews(
            Instant from,
            Instant to
    ) {
        return jdbcTemplate.queryForObject(
                COUNT_AI_REVIEWS,
                periodParameters(from, to),
                (rs, rowNum) -> new AiReviewCounts(
                        rs.getLong("users"),
                        rs.getLong("requests"),
                        rs.getLong("completed"),
                        rs.getLong("failed")
                )
        );
    }

    /** 모든 쿼리가 공통으로 쓰는 제외 회원 ID 파라미터에서 시작한다. */
    private MapSqlParameterSource parameters() {
        return new MapSqlParameterSource().addValue("excludedUserIds", excludedUserIds);
    }

    private MapSqlParameterSource periodParameters(
            Instant from,
            Instant to
    ) {
        return parameters()
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
    }

    private long count(
            String sql,
            MapSqlParameterSource parameters
    ) {
        Long count = jdbcTemplate.queryForObject(
                sql,
                parameters,
                Long.class
        );
        return count == null ? 0L : count;
    }
}
