package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.domain.PushDeliveryStatus;
import com.chalkak.backend.notification.domain.PushDeviceDeliveryStatus;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PushDeliveryRepositoryImplTest extends IntegrationTestSupport {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Autowired
    private DataSource dataSource;
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
    @DisplayName("같은 사건·회원으로 다시 생성해도 기존 기기 결과와 완료 상태를 보존한다")
    void createIfAbsent_existingDelivery_preservesHistory() {
        // Given
        PushDelivery first = create();
        UUID deviceId = UUID.randomUUID();
        var accepted = started().finish(
                PushDeviceDeliveryStatus.ACCEPTED,
                null,
                Instant.now()
        );
        repository.saveDeviceResult(
                first.getId(),
                deviceId,
                accepted
        );
        repository.complete(first.getId());

        // When
        PushDelivery second = create();

        // Then
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.isCompleted()).isTrue();
        assertThat(second.getDeviceResults()).containsEntry(deviceId, accepted);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_deliveries", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사건도 수신 회원이 다르면 별도 발송 이력을 만든다")
    void createIfAbsent_differentUsers_keepsSeparateDeliveries() {
        // Given
        UUID otherUser = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,email,signature_original_storage_key)
                VALUES (?, 'other-delivery@example.com', 'delivery/other')
                """, otherUser);
        PushDelivery first = create();

        // When
        PushDelivery other = repository.createIfAbsent(EVENT_ID, otherUser).orElseThrow();

        // Then
        assertThat(other.getId()).isNotEqualTo(first.getId());
        assertThat(other.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
    }

    @Test
    @DisplayName("동시에 같은 이력을 생성해도 유니크 제약으로 하나의 행을 반환한다")
    void createIfAbsent_concurrentAttempts_returnsOneRecord() throws Exception {
        // Given
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return create();
            });
            var second = pool.submit(() -> {
                start.await();
                return create();
            });

            // When
            start.countDown();

            // Then
            assertThat(first.get(5, TimeUnit.SECONDS).getId())
                    .isEqualTo(second.get(5, TimeUnit.SECONDS).getId());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM push_deliveries", Integer.class))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("존재하지 않는 회원의 이력은 생성하지 않는다")
    void createIfAbsent_missingUser_returnsEmpty() {
        // When & Then
        assertThat(repository.createIfAbsent(EVENT_ID, UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("기기 JSON 부분 갱신은 다른 기기의 수락 기록을 보존하고 완료 행은 변경하지 않는다")
    void saveDeviceResult_multipleDevices_preservesResultsAndCompletion() {
        // Given
        PushDelivery delivery = create();
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
                acceptedDevice,
                accepted
        )).isTrue();
        assertThat(repository.saveDeviceResult(
                delivery.getId(),
                retryDevice,
                started()
        )).isTrue();
        assertThat(repository.complete(delivery.getId())).isTrue();
        PushDelivery saved = create();

        // Then
        assertThat(saved.isCompleted()).isTrue();
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getDeviceResults()).containsEntry(acceptedDevice, accepted)
                .containsKey(retryDevice);
        assertThat(repository.saveDeviceResult(
                delivery.getId(),
                retryDevice,
                started()
        )).isFalse();
        assertThat(repository.complete(delivery.getId())).isFalse();
    }

    @Test
    @DisplayName("기록이 사라지면 결과 저장과 완료 갱신은 실패를 반환한다")
    void update_missingDelivery_returnsFalse() {
        // When & Then
        UUID missing = UUID.randomUUID();
        assertThat(repository.saveDeviceResult(
                missing,
                UUID.randomUUID(),
                started()
        )).isFalse();
        assertThat(repository.complete(missing)).isFalse();
    }

    @Test
    @DisplayName("조건부 갱신 후 조회는 이전 영속 객체 대신 최신 기기 결과와 완료 상태를 반환한다")
    void update_withPreviouslyLoadedEntity_returnsFreshResults() {
        // Given
        UUID deviceId = UUID.randomUUID();
        var accepted = started().finish(
                PushDeviceDeliveryStatus.ACCEPTED,
                null,
                Instant.now()
        );
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            PushDelivery previous = create();

            // When
            assertThat(repository.saveDeviceResult(
                    previous.getId(),
                    deviceId,
                    accepted
            )).isTrue();
            PushDelivery updated = create();
            assertThat(repository.complete(previous.getId())).isTrue();
            PushDelivery completed = create();

            // Then
            assertThat(updated).isNotSameAs(previous);
            assertThat(previous.getDeviceResults()).isEmpty();
            assertThat(updated.getDeviceResults()).containsEntry(deviceId, accepted);
            assertThat(completed.isCompleted()).isTrue();
        });
    }

    @Test
    @DisplayName("이력 생성 트랜잭션이 롤백되면 행이 남지 않는다")
    void createIfAbsent_transactionRollsBack_leavesNoRecord() {
        // When & Then
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            create();
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findByEventIdAndUserId(EVENT_ID, USER_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "status='INVALID'",
            "status='PROCESSING'",
            "status='COMPLETED'",
            "completed_at=clock_timestamp()",
            "device_results='[]'::jsonb"
    })
    @DisplayName("DB는 허용하지 않는 상태와 완료 시각·JSON 형식의 불일치를 거절한다")
    void update_invalidStoredState_rejectsConstraintViolation(String assignment) {
        // Given
        create();

        // When & Then
        assertThatThrownBy(() -> jdbc.update("UPDATE push_deliveries SET " + assignment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("발송 이력 생성 마이그레이션 하나로 최종 스키마를 만들고 기존 회원을 보존한다")
    void migrate_existingUsers_createsFinalDeliverySchema() throws Exception {
        // Given
        String schema = "push_delivery_migration_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            migrate(schema, "202610212350");
            try (Connection connection = dataSource.getConnection()) {
                String previousSchema = connection.getSchema();
                connection.setSchema(schema);
                try {
                    JdbcTemplate isolated = new JdbcTemplate(
                            new SingleConnectionDataSource(connection, true));
                    isolated.update(
                            """
                                    INSERT INTO users(id, email, signature_original_storage_key,
                                        topic_push_enabled, moderation_push_enabled)
                                    VALUES (?, 'existing-delivery@example.com', 'delivery/existing', FALSE, TRUE)
                                    """,
                            USER_ID);
                    Map<String, Object> before = isolated.queryForMap("SELECT * FROM users");

                    // When
                    migrate(schema, "202610262100");

                    // Then
                    assertThat(isolated.queryForMap("SELECT * FROM users")).isEqualTo(before);
                    assertThat(isolated.queryForList(
                            """
                                    SELECT column_name FROM information_schema.columns
                                    WHERE table_schema = ? AND table_name = 'push_deliveries'
                                    """,
                            String.class,
                            schema
                    )).containsExactlyInAnyOrder(
                            "id",
                            "event_id",
                            "user_id",
                            "status",
                            "device_results",
                            "completed_at",
                            "created_at",
                            "updated_at"
                    );
                    assertThat(isolated.queryForObject("""
                            SELECT count(*) FROM flyway_schema_history
                            WHERE version IN ('202610262100', '202610271200')
                            """, Integer.class)).isEqualTo(1);
                    Map<String, Object> created = isolated.queryForMap(
                            """
                                    INSERT INTO push_deliveries(event_id, user_id) VALUES (?, ?)
                                    RETURNING id, status, device_results::text, completed_at,
                                        created_at, updated_at
                                    """,
                            EVENT_ID,
                            USER_ID
                    );
                    assertThat(created.get("id")).isNotNull();
                    assertThat(created.get("status")).isEqualTo("PENDING");
                    assertThat(created.get("device_results")).isEqualTo("{}");
                    assertThat(created.get("completed_at")).isNull();
                    assertThat(created.get("created_at")).isNotNull();
                    assertThat(created.get("updated_at")).isNotNull();
                } finally {
                    connection.setSchema(previousSchema);
                }
            }
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private void migrate(String schema, String target) {
        Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private PushDelivery create() {
        return repository.createIfAbsent(EVENT_ID, USER_ID).orElseThrow();
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
}
