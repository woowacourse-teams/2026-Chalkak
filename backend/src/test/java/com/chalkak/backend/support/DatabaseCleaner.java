package com.chalkak.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 트랜잭션 롤백으로 격리할 수 없는 notification 통합 테스트의 데이터를 정리한다.
 */
public final class DatabaseCleaner {

    private static final String TRUNCATE_NOTIFICATION_TEST_TABLES = """
            TRUNCATE TABLE
                notification_outboxes,
                posts,
                photos,
                topics,
                users
            RESTART IDENTITY CASCADE
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void cleanNotificationTestData() {
        jdbcTemplate.execute(TRUNCATE_NOTIFICATION_TEST_TABLES);
    }
}
