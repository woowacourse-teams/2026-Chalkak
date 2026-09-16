package com.chalkak.backend.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import com.chalkak.backend.auth.repository.ConsumedSignupTokenRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 jti를 기록하는 두 트랜잭션이 실제로 겹쳐야 검증되므로 {@code @Transactional} 격리를 쓰지 않고 직접 정리한다.
 */
class ConsumedSignupTokenRepositoryConcurrencyTest extends IntegrationTestSupport {

    private static final String JTI = "0198fd30-0000-7000-8000-00000000413a";
    private static final Duration BLOCKING_CHECK_TIMEOUT = Duration.ofMillis(300);

    @Autowired
    private ConsumedSignupTokenRepository consumedSignupTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("같은 jti를 기록 중인 트랜잭션이 커밋되면 기다리던 트랜잭션은 예외 없이 실패를 반환한다")
    void consumeIfAbsent_concurrentSameJtiAfterCommit_returnsFalseWithoutException()
            throws Exception {
        // When
        ConcurrentConsumption consumption = consumeWhileFirstTransactionOpen(false);

        // Then
        assertThat(consumption.first()).isTrue();
        assertThat(consumption.second()).isFalse();
        assertThat(countTokens()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 jti를 기록 중인 트랜잭션이 롤백되면 기다리던 트랜잭션이 기록에 성공한다")
    void consumeIfAbsent_concurrentSameJtiAfterRollback_returnsTrue() throws Exception {
        // When
        ConcurrentConsumption consumption = consumeWhileFirstTransactionOpen(true);

        // Then
        assertThat(consumption.first()).isTrue();
        assertThat(consumption.second()).isTrue();
        assertThat(countTokens()).isEqualTo(1);
    }

    /**
     * 첫 트랜잭션이 jti를 기록한 채 열려 있는 동안 두 번째 트랜잭션이 같은 jti를 기록하게 한다. 두 번째가 첫 트랜잭션의 종료를
     * 기다리는지 확인한 뒤 첫 트랜잭션을 끝낸다.
     */
    private ConcurrentConsumption consumeWhileFirstTransactionOpen(boolean rollbackFirst)
            throws Exception {
        CountDownLatch firstConsumed = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> inTransaction(status -> {
                boolean consumed = consume();
                firstConsumed.countDown();
                awaitLatch(finishFirst);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return consumed;
            }));
            assertThat(firstConsumed.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() -> inTransaction(status -> consume()));

            assertThatThrownBy(() -> second.get(
                    BLOCKING_CHECK_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            finishFirst.countDown();

            return new ConcurrentConsumption(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
        }
    }

    private Boolean inTransaction(
            TransactionCallback<Boolean> action
    ) {
        return new TransactionTemplate(transactionManager).execute(action);
    }

    private boolean consume() {
        return consumedSignupTokenRepository.consumeIfAbsent(ConsumedSignupToken.create(
                JTI,
                Instant.now().plus(Duration.ofMinutes(5))));
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private int countTokens() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consumed_signup_tokens WHERE jti = ?",
                Integer.class,
                JTI);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM consumed_signup_tokens WHERE jti = ?", JTI);
    }

    private record ConcurrentConsumption(
            boolean first,
            boolean second) {
    }
}
