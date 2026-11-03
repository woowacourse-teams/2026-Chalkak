package com.chalkak.backend.notification.support;

import com.chalkak.backend.notification.domain.FcmToken;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 실제 DB Worker 테스트에서 기기와 연결할 유효한 로그인 세션을 준비한다. */
public final class PushSessionFixtures {

    private PushSessionFixtures() {
    }

    public static UUID insertSession(
            JdbcTemplate jdbc,
            UUID userId,
            Instant now
    ) {
        UUID sessionId = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO user_refresh_tokens
                            (user_id, session_id, token_hash, expires_at, absolute_expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                userId,
                sessionId,
                new FcmToken(UUID.randomUUID().toString()).getHash(),
                Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now.plusSeconds(7200))
        );
        return sessionId;
    }
}
