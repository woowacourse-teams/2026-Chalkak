package com.chalkak.backend.like.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** 직접 SQL로 준비한 테스트 데이터의 집계만 맞춘다. 서비스 호출 결과를 보정하는 데 사용하지 않는다. */
public final class PostLikeCountFixtures {

    private PostLikeCountFixtures() {
    }

    public static void synchronizeFixtureCounts(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("""
                INSERT INTO post_like_counts (post_id, like_count)
                SELECT post.id, COUNT(post_like.id)
                FROM posts post
                LEFT JOIN post_likes post_like ON post_like.post_id = post.id
                GROUP BY post.id
                ON CONFLICT (post_id) DO UPDATE SET like_count = EXCLUDED.like_count
                """);
    }
}
