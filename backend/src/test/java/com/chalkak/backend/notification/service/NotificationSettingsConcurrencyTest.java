package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationSettingsConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private NotificationSettingsService notificationSettingsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("두 기기에서 서로 다른 설정을 수정하면 앞선 커밋을 기다리고 두 변경을 모두 보존한다")
    void updateSettings_concurrentPartialUpdates_preservesBothChanges(boolean topicFirst)
            throws Exception {
        // Given
        UUID userId = userRepository.save(UserFixture.create()).getId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch firstCanCommit = new CountDownLatch(1);
        CompletableFuture<Integer> secondBackendPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                notificationSettingsService.updateSettings(
                        userId, topicFirst ? false : null, topicFirst ? null : false);
                firstUpdated.countDown();
                awaitCommit(firstCanCommit);
            }));
            assertThat(firstUpdated.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> transaction.executeWithoutResult(status -> {
                secondBackendPid.complete(
                        jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                notificationSettingsService.updateSettings(
                        userId, topicFirst ? null : false, topicFirst ? false : null);
            }));

            // When
            awaitBlocked(secondBackendPid.get(5, TimeUnit.SECONDS));
            assertThat(second.isDone()).isFalse();
            firstCanCommit.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(notificationSettingsService.getSettings(userId))
                    .isEqualTo(new NotificationSettingsResult(false, false));
        } finally {
            firstCanCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitCommit(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("첫 번째 설정 수정의 커밋 허용을 기다리지 못했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }

    private void awaitBlocked(int backendPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbcTemplate.queryForObject(
                    "SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, backendPid);
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("두 번째 설정 수정이 DB 잠금을 기다리지 않았습니다.");
    }
}
