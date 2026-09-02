package com.chalkak.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** 트랜잭션 롤백으로 격리할 수 없는 통합 테스트 데이터를 정리한다. */
public final class DatabaseCleaner {

    private static final String TRUNCATE_TEST_TABLES = """
            TRUNCATE TABLE
                posts,
                photos,
                post_image_uploads,
                topics,
                users
            RESTART IDENTITY CASCADE
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.execute(TRUNCATE_TEST_TABLES);
    }
}
