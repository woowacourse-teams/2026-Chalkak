package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PushDeviceJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PushDeviceJpaRepository pushDeviceJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 로그인에는 두 기기 행을 저장할 수 없다")
    void insert_duplicateSession_rejectsSecondRow() {
        // given
        UUID sessionId = newSession();
        insert(sessionId, "first-token", new FcmToken("first-token").getHash());

        // when & then
        assertThatThrownBy(() -> insert(sessionId, "second-token",
                new FcmToken("second-token").getHash()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_push_devices_session");
    }

    @Test
    @DisplayName("같은 FCM 토큰을 두 기기에 저장할 수 없다")
    void insert_duplicateToken_rejectsSecondBinding() {
        // given
        UUID first = newSession();
        UUID second = newSession();
        String hash = new FcmToken("shared-token").getHash();
        insert(first, "shared-token", hash);

        // when & then
        assertThatThrownBy(() -> insert(second, "shared-token", hash))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_push_devices_fcm_token");
    }

    @Test
    @DisplayName("이전 기기 행을 삭제하면 같은 토큰을 다른 로그인에 저장할 수 있다")
    void insert_deletedDuplicateToken_allowsNewBinding() {
        // given
        UUID first = newSession();
        UUID second = newSession();
        String hash = new FcmToken("shared-token").getHash();
        insert(first, "shared-token", hash);
        pushDeviceJpaRepository.deleteBySessionId(first);
        entityManager.flush();
        entityManager.clear();

        // when
        insert(second, "shared-token", hash);

        // then
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM push_devices WHERE fcm_token_hash = ?
                """, Integer.class, hash)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"token,", ",invalid-hash", "token,invalid-hash"})
    @DisplayName("토큰 원문과 올바른 해시 중 하나만 저장할 수 없다")
    void insert_incompleteOrInvalidTokenPair_rejectsRow(String token, String hash) {
        // given
        UUID sessionId = newSession();

        // when & then
        assertThatThrownBy(() -> insert(sessionId, token, hash))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_id", "session_id"})
    @DisplayName("기기에는 회원과 로그인 연결이 모두 있어야 한다")
    void insert_deviceWithoutIdentity_rejectsRow(String column) {
        // given
        UUID sessionId = newSession();
        UUID userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_refresh_tokens WHERE session_id = ?", UUID.class,
                sessionId);
        String token = "device-token";

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO push_devices (user_id, session_id, fcm_token, fcm_token_hash,
                    registered_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, column.equals("user_id") ? null : userId,
                column.equals("session_id") ? null : sessionId, token,
                new FcmToken(token).getHash()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(column);
    }

    private UUID newSession() {
        User user = userRepository.save(UserFixture.create());
        UUID sessionId = userRefreshTokenService.issue(user).sessionId();
        entityManager.flush();
        entityManager.clear();
        return sessionId;
    }

    private void insert(UUID sessionId, String token, String hash) {
        jdbcTemplate.update("""
                INSERT INTO push_devices (user_id, session_id, fcm_token, fcm_token_hash,
                    registered_at, updated_at)
                VALUES ((SELECT user_id FROM user_refresh_tokens WHERE session_id = ? LIMIT 1),
                    ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, sessionId, sessionId, token, hash);
    }
}
