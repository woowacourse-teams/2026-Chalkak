package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LoginSessionServiceTest extends IntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    @Autowired
    private LoginSessionService service;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private UUID userId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        userId = users.save(UserFixture.create()).getId();
        entityManager.flush();
        sessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_refresh_tokens
                    (user_id, session_id, token_hash, expires_at, absolute_expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, sessionId, "a".repeat(64), Timestamp.from(NOW.plusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(7200)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "BANNED"})
    @DisplayName("탈퇴하지 않은 회원의 유효한 로그인은 회원을 반환하고 정지 회원도 허용한다")
    void findUsableUser_liveSession_returnsUser(String status) {
        // Given
        jdbc.update("UPDATE users SET status=CAST(? AS user_status) WHERE id=?", status, userId);
        entityManager.flush();
        entityManager.clear();
        // When
        var result = service.findUsableUser(userId, sessionId, NOW);
        // Then
        assertThat(result).hasValueSatisfying(user -> assertThat(user.getId()).isEqualTo(userId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"withdrawn", "revoked", "rotated", "expired", "absoluteExpired",
            "missingToken", "otherOwner", "unknownSession", "missingUser"})
    @DisplayName("탈퇴·폐기·회전·만료·소유자 불일치와 없는 로그인은 예외 없이 빈 결과를 반환한다")
    void findUsableUser_unusableSession_returnsEmpty(String state) {
        // Given
        UUID requestedUserId = userId;
        UUID requestedSessionId = sessionId;
        if (state.equals("withdrawn")) {
            jdbc.update("UPDATE users SET deleted_at=? WHERE id=?", Timestamp.from(NOW), userId);
        }
        if (state.equals("revoked")) {
            jdbc.update("UPDATE user_refresh_tokens SET revoked_at=? WHERE session_id=?",
                    Timestamp.from(NOW), sessionId);
        }
        if (state.equals("rotated")) {
            jdbc.update("UPDATE user_refresh_tokens SET rotated_at=? WHERE session_id=?",
                    Timestamp.from(NOW), sessionId);
        }
        if (state.equals("expired")) {
            jdbc.update("UPDATE user_refresh_tokens SET expires_at=? WHERE session_id=?",
                    Timestamp.from(NOW), sessionId);
        }
        if (state.equals("absoluteExpired")) {
            jdbc.update("""
                    UPDATE user_refresh_tokens SET expires_at=?, absolute_expires_at=?
                    WHERE session_id=?
                    """, Timestamp.from(NOW), Timestamp.from(NOW), sessionId);
        }
        if (state.equals("missingToken")) {
            jdbc.update("DELETE FROM user_refresh_tokens WHERE session_id=?", sessionId);
        }
        if (state.equals("otherOwner")) {
            User other = users.save(UserFixture.create());
            entityManager.flush();
            requestedUserId = other.getId();
        }
        if (state.equals("unknownSession")) {
            requestedSessionId = UUID.randomUUID();
        }
        if (state.equals("missingUser")) {
            requestedUserId = UUID.randomUUID();
        }
        entityManager.flush();
        entityManager.clear();
        // When
        var result = service.findUsableUser(requestedUserId, requestedSessionId, NOW);
        // Then
        assertThat(result).isEmpty();
    }
}
