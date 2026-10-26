package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PushDeviceDeliveryStatus;

import com.chalkak.backend.notification.domain.PushDeliveryStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PushDeliveryRepositoryImplTest extends IntegrationTestSupport {
    private static final Duration LIFETIME = Duration.ofSeconds(60);
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Autowired
    private PushDeliveryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        jdbc.update("""
                INSERT INTO users(id,email,signature_original_storage_key)
                VALUES (?, 'delivery-test@example.com', 'delivery/signature')
                """, USER_ID);
    }

    @Test
    @DisplayName("같은 사건·회원은 하나의 기록만 만들고 Lease를 보유하면 다른 시도가 획득하지 못한다")
    void claim_sameEventAndUser_keepsOneRowAndOneOwner() {
        // When
        PushDelivery first = claim(UUID.randomUUID());
        var second = repository.claim(
                EVENT_ID,
                USER_ID,
                UUID.randomUUID(),
                LIFETIME
        );

        // Then
        assertThat(second).isEmpty();
        assertThat(first.getStatus()).isEqualTo(PushDeliveryStatus.PROCESSING);
        assertThat(first.getLeaseUntil()).isAfter(first.getCreatedAt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_deliveries", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사건도 수신 회원이 다르면 별도 기록과 Lease를 가진다")
    void claim_sameEventForDifferentUsers_keepsSeparateDeliveries() {
        // Given
        UUID otherUser = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,email,signature_original_storage_key)
                VALUES (?, 'other-delivery@example.com', 'delivery/other')
                """, otherUser);
        PushDelivery first = claim(UUID.randomUUID());

        // When
        PushDelivery other = repository.claim(
                EVENT_ID,
                otherUser,
                UUID.randomUUID(),
                LIFETIME
        ).orElseThrow();

        // Then
        assertThat(other.getId()).isNotEqualTo(first.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_deliveries", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("동시에 같은 작업을 확보해도 한 시도만 Lease를 획득한다")
    void claim_concurrentAttempts_grantsOnlyOneLease() throws Exception {
        // Given
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return repository.claim(
                        EVENT_ID,
                        USER_ID,
                        UUID.randomUUID(),
                        LIFETIME
                );
            });
            var second = pool.submit(() -> {
                start.await();
                return repository.claim(
                        EVENT_ID,
                        USER_ID,
                        UUID.randomUUID(),
                        LIFETIME
                );
            });

            // When
            start.countDown();
            int owners = 0;
            if (first.get(5, TimeUnit.SECONDS).isPresent()) {
                owners++;
            }
            if (second.get(5, TimeUnit.SECONDS).isPresent()) {
                owners++;
            }

            // Then
            assertThat(owners).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM push_deliveries", Integer.class))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("기기별 JSON 일부만 갱신해 다른 기기의 수락 결과를 유지하고 완료 후 재획득을 막는다")
    void saveDeviceResult_multipleDevices_preservesResultsAndCompletion() {
        // Given
        UUID token = UUID.randomUUID();
        PushDelivery delivery = claim(token);
        UUID acceptedDevice = UUID.randomUUID();
        UUID retryDevice = UUID.randomUUID();
        var accepted = started().finish(
                PushDeviceDeliveryStatus.ACCEPTED,
                null,
                Instant.now()
        );

        // When
        assertThat(repository.saveDeviceResult(
                delivery.getId(),
                token,
                acceptedDevice,
                accepted
        )).isTrue();
        assertThat(repository.saveDeviceResult(
                delivery.getId(),
                token,
                retryDevice,
                started()
        )).isTrue();
        assertThat(repository.complete(delivery.getId(), token)).isTrue();
        PushDelivery saved = repository.findByEventIdAndUserId(EVENT_ID, USER_ID).orElseThrow();

        // Then
        assertThat(saved.isCompleted()).isTrue();
        assertThat(saved.getLeaseToken()).isNull();
        assertThat(saved.getLeaseUntil()).isNull();
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getDeviceResults()).containsEntry(acceptedDevice, accepted)
                .containsKey(retryDevice);
        assertThat(repository.claim(
                EVENT_ID,
                USER_ID,
                UUID.randomUUID(),
                LIFETIME
        )).isEmpty();
    }

    @Test
    @DisplayName("같은 트랜잭션에서 조건부 갱신 후 조회해도 이전 영속 객체 대신 최신 결과를 반환한다")
    void update_withPreviouslyLoadedEntity_returnsFreshDeviceResultsAndCompletion() {
        // Given
        UUID token = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        var accepted = started().finish(
                PushDeviceDeliveryStatus.ACCEPTED,
                null,
                Instant.now()
        );

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            PushDelivery previous = claim(token);

            // When
            assertThat(repository.saveDeviceResult(
                    previous.getId(),
                    token,
                    deviceId,
                    accepted
            )).isTrue();
            PushDelivery updated = repository.findByEventIdAndUserId(EVENT_ID, USER_ID)
                    .orElseThrow();
            assertThat(repository.complete(previous.getId(), token)).isTrue();
            PushDelivery completed = repository.findByEventIdAndUserId(EVENT_ID, USER_ID)
                    .orElseThrow();

            // Then
            assertThat(updated).isNotSameAs(previous);
            assertThat(previous.getDeviceResults()).isEmpty();
            assertThat(updated.getDeviceResults()).containsEntry(deviceId, accepted);
            assertThat(completed.isCompleted()).isTrue();
            assertThat(completed.getLeaseToken()).isNull();
        });
    }

    @Test
    @DisplayName("만료된 Lease는 갱신·기기 저장·완료·해제를 거부하고 새 소유자에게만 갱신을 허용한다")
    void update_expiredOrReplacedLease_rejectsStaleOwner() {
        // Given
        UUID oldToken = UUID.randomUUID();
        PushDelivery old = claim(oldToken);
        jdbc.update("UPDATE push_deliveries SET lease_until=clock_timestamp()-INTERVAL '1 second'");

        // When & Then
        assertRejected(old.getId(), oldToken);
        UUID newToken = UUID.randomUUID();
        PushDelivery current = claim(newToken);
        assertRejected(old.getId(), oldToken);
        assertThat(repository.renew(
                current.getId(),
                newToken,
                Duration.ofSeconds(120)
        )).isTrue();
        assertThat(
                repository.findByEventIdAndUserId(EVENT_ID, USER_ID).orElseThrow().getLeaseUntil())
                .isAfter(current.getLeaseUntil());
        assertThat(repository.release(current.getId(), newToken)).isTrue();
        assertThat(repository.findByEventIdAndUserId(EVENT_ID, USER_ID).orElseThrow().getStatus())
                .isEqualTo(PushDeliveryStatus.PENDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "status='INVALID'",
            "status='PENDING'",
            "completed_at=clock_timestamp()",
            "device_results='[]'::jsonb"
    })
    @DisplayName("DB는 허용하지 않는 상태·Lease·완료 시각·JSON 형식을 거절한다")
    void update_invalidStoredState_rejectsConstraintViolation(String assignment) {
        // Given
        claim(UUID.randomUUID());

        // When & Then
        assertThatThrownBy(() -> jdbc.update("UPDATE push_deliveries SET " + assignment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private PushDelivery claim(UUID token) {
        return repository.claim(
                EVENT_ID,
                USER_ID,
                token,
                LIFETIME
        ).orElseThrow();
    }

    private PushDelivery.DeviceResult started() {
        return new PushDelivery.DeviceResult(
                PushDeviceDeliveryStatus.SENDING,
                1,
                null,
                Instant.now(),
                null
        );
    }

    private void assertRejected(UUID id, UUID token) {
        assertThat(repository.renew(
                id,
                token,
                LIFETIME
        )).isFalse();
        assertThat(repository.saveDeviceResult(
                id,
                token,
                UUID.randomUUID(),
                started()
        )).isFalse();
        assertThat(repository.complete(id, token)).isFalse();
        assertThat(repository.release(id, token)).isFalse();
    }
}
