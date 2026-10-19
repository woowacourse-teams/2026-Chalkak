package com.chalkak.backend.post.support;

import com.chalkak.backend.post.domain.ModerationStatus;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AuthorPostFixtures {

    private AuthorPostFixtures() {
    }

    public static UUID createUser(JdbcTemplate jdbcTemplate) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key)
                VALUES (?, ?, 'ACTIVE', ?)
                """, id, id + "@author-post.test", "chalkak/test/signatures/" + id + ".png");
        return id;
    }

    public static UUID createPost(
            JdbcTemplate jdbcTemplate,
            UUID authorId,
            LocalDate topicDate,
            ModerationStatus status
    ) {
        UUID postId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        ZoneId kst = ZoneId.of("Asia/Seoul");
        jdbcTemplate.update("""
                INSERT INTO topics (id, title, topic_date, starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?)
                """, topicId, "주제 " + topicDate, topicDate,
                Timestamp.from(topicDate.atStartOfDay(kst).toInstant()),
                Timestamp.from(topicDate.plusDays(1).atStartOfDay(kst).toInstant()));
        jdbcTemplate.update("""
                INSERT INTO photos (id, original_storage_key, thumbnail_storage_key)
                VALUES (?, ?, ?)
                """, photoId, "chalkak/test/posts/" + postId + ".webp",
                "chalkak/test/posts/" + postId + "-thumbnail.webp");
        jdbcTemplate.update("""
                INSERT INTO posts (id, user_id, topic_id, photo_id, moderation_status)
                VALUES (?, ?, ?, ?, ?::moderation_status)
                """, postId, authorId, topicId, photoId, status.name());
        return postId;
    }
}
