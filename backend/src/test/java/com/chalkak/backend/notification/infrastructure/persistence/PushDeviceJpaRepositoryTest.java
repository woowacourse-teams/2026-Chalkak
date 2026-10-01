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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 로그인에는 두 기기 행을 저장할 수 없다")
    void insert_duplicateSession_rejectsSecondRow() {
        // given
        UUID sessionId = newSession();
        insert(sessionId, "first-token", new FcmToken("first-token").getHash(), null);

        // when & then
        assertThatThrownBy(() -> insert(sessionId, "second-token",
                new FcmToken("second-token").getHash(), null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_push_devices_session");
    }

    @Test
    @DisplayName("같은 FCM 토큰을 두 활성 기기에 저장할 수 없다")
    void insert_duplicateActiveToken_rejectsSecondBinding() {
        // given
        UUID first = newSession();
        UUID second = newSession();
        String hash = new FcmToken("shared-token").getHash();
        insert(first, "shared-token", hash, null);

        // when & then
        assertThatThrownBy(() -> insert(second, "shared-token", hash, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_push_devices_active_fcm_token");
    }

    @Test
    @DisplayName("이전 연결이 비활성화되면 같은 토큰을 다른 로그인에 저장할 수 있다")
    void insert_disabledDuplicateToken_allowsActiveBinding() {
        // given
        UUID first = newSession();
        UUID second = newSession();
        String hash = new FcmToken("shared-token").getHash();
        insert(first, "shared-token", hash, Instant.now());

        // when
        insert(second, "shared-token", hash, null);

        // then
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM push_devices WHERE fcm_token_hash = ? AND disabled_at IS NULL
                """, Integer.class, hash)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"token,", ",invalid-hash", "token,invalid-hash"})
    @DisplayName("토큰 원문과 올바른 해시 중 하나만 저장할 수 없다")
    void insert_incompleteOrInvalidTokenPair_rejectsRow(String token, String hash) {
        // given
        UUID sessionId = newSession();

        // when & then
        assertThatThrownBy(() -> insert(sessionId, token, hash, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_push_devices_token_pair");
    }

    @Test
    @DisplayName("활성 기기에는 FCM 토큰이 있어야 한다")
    void insert_activeDeviceWithoutToken_rejectsRow() {
        // given
        UUID sessionId = newSession();

        // when & then
        assertThatThrownBy(() -> insert(sessionId, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_push_devices_active_token");
    }

    private UUID newSession() {
        User user = userRepository.save(UserFixture.create());
        UUID sessionId = userRefreshTokenService.issue(user).sessionId();
        entityManager.flush();
        entityManager.clear();
        return sessionId;
    }

    private void insert(UUID sessionId, String token, String hash, Instant disabledAt) {
        jdbcTemplate.update("""
                INSERT INTO push_devices (session_id, fcm_token, fcm_token_hash,
                    registered_at, updated_at, disabled_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """, sessionId, token, hash,
                disabledAt == null ? null : Timestamp.from(disabledAt));
    }
}
