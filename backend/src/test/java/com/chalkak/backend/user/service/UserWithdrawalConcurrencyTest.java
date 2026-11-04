package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.chalkak.backend.exception.BaseException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.post.support.AuthorPostFixtures;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.infrastructure.persistence.UserRepositoryImpl;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 탈퇴와 겹친 탈퇴·게시물 수정이 PostgreSQL 회원 행 잠금으로 직렬화되는지 검증한다. */
class UserWithdrawalConcurrencyTest extends IntegrationTestSupport {

    private static final String WITHDRAWAL_THREAD = "user-withdrawal";
    private static final String SECOND_WITHDRAWAL_THREAD = "user-second-withdrawal";
    private static final String UPDATE_THREAD = "post-consent-update";

    @Autowired
    private UserService userService;

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private UserRepositoryImpl userRepositorySpy;

    private DatabaseCleaner databaseCleaner;
    private UUID userId;
    private UUID postId;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        databaseCleaner.clean();
        User user = userRepositorySpy.save(UserFixture.create());
        userId = user.getId();
        postId = AuthorPostFixtures.createPost(
                jdbcTemplate,
                userId,
                LocalDate.now(ZoneId.of("Asia/Seoul")),
                ModerationStatus.APPROVED
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("겹친 두 번째 탈퇴는 첫 탈퇴 커밋을 기다린 뒤 거부되어 탈퇴·철회 시각을 바꾸지 않는다")
    void completeWithdrawal_duplicateRequests_keepsFirstWithdrawalTime() throws Exception {
        agreeInstagramUpload();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch firstCanCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(SECOND_WITHDRAWAL_THREAD)) {
                secondStarted.countDown();
            }
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(WITHDRAWAL_THREAD)) {
                firstLocked.countDown();
                await(firstCanCommit, "첫 탈퇴 커밋이 허용되지 않았습니다.");
            }
            return result;
        }).when(userRepositorySpy).findActiveByIdForUpdate(userId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> first = executor.submit(() -> runNamed(WITHDRAWAL_THREAD, this::withdraw));
        await(firstLocked, "첫 탈퇴가 회원 락을 획득하지 못했습니다.");
        Future<String> second = executor.submit(() -> runNamed(SECOND_WITHDRAWAL_THREAD, this::withdraw));

        try {
            await(secondStarted, "두 번째 탈퇴가 회원 락 조회를 시작하지 못했습니다.");
            assertThat(second.isDone()).isFalse();
        } finally {
            firstCanCommit.countDown();
        }
        String firstOutcome = first.get(5, TimeUnit.SECONDS);
        String secondOutcome = second.get(5, TimeUnit.SECONDS);
        shutdown(executor);

        assertThat(firstOutcome).isEqualTo("SUCCESS");
        assertThat(secondOutcome).isEqualTo("UNAUTHORIZED");
        Map<String, Object> post = findPostConsent();
        assertThat(post.get("instagram_upload_agreed")).isEqualTo(false);
        assertThat(post.get("instagram_id")).isNull();
        assertThat(post.get("instagram_revoked_at")).isEqualTo(findUserDeletedAt());
    }

    @Test
    @DisplayName("동의를 켜는 수정이 먼저 시작되면 탈퇴는 수정 커밋을 기다린 뒤 그 동의를 철회한다")
    void withdrawAndUpdate_updateLocksFirst_revokesUpdatedConsent() throws Exception {
        CountDownLatch updateLocked = new CountDownLatch(1);
        CountDownLatch withdrawalStarted = new CountDownLatch(1);
        CountDownLatch updateCanCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(UPDATE_THREAD)) {
                updateLocked.countDown();
                await(updateCanCommit, "수정 커밋이 허용되지 않았습니다.");
            }
            return result;
        }).when(userRepositorySpy).findActiveByIdForShare(userId);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(WITHDRAWAL_THREAD)) {
                withdrawalStarted.countDown();
            }
            return invocation.callRealMethod();
        }).when(userRepositorySpy).findActiveByIdForUpdate(userId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> update = executor.submit(() -> runNamed(UPDATE_THREAD, this::agreeByUpdate));
        await(updateLocked, "수정이 회원 공유 락을 획득하지 못했습니다.");
        Future<String> withdrawal = executor.submit(() -> runNamed(WITHDRAWAL_THREAD, this::withdraw));

        try {
            await(withdrawalStarted, "탈퇴가 회원 락 조회를 시작하지 못했습니다.");
            assertThat(withdrawal.isDone()).isFalse();
        } finally {
            updateCanCommit.countDown();
        }
        String updateOutcome = update.get(5, TimeUnit.SECONDS);
        String withdrawalOutcome = withdrawal.get(5, TimeUnit.SECONDS);
        shutdown(executor);

        assertThat(updateOutcome).isEqualTo("SUCCESS");
        assertThat(withdrawalOutcome).isEqualTo("SUCCESS");
        Map<String, Object> post = findPostConsent();
        assertThat(post.get("instagram_upload_agreed")).isEqualTo(false);
        assertThat(post.get("instagram_id")).isNull();
        assertThat(post.get("instagram_revoked_at")).isEqualTo(findUserDeletedAt());
    }

    @Test
    @DisplayName("탈퇴가 먼저 시작되면 동의를 켜는 수정은 탈퇴 커밋을 기다린 뒤 거부된다")
    void withdrawAndUpdate_withdrawalLocksFirst_rejectsUpdate() throws Exception {
        CountDownLatch withdrawalLocked = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);
        CountDownLatch withdrawalCanCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(WITHDRAWAL_THREAD)) {
                withdrawalLocked.countDown();
                await(withdrawalCanCommit, "탈퇴 커밋이 허용되지 않았습니다.");
            }
            return result;
        }).when(userRepositorySpy).findActiveByIdForUpdate(userId);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(UPDATE_THREAD)) {
                updateStarted.countDown();
            }
            return invocation.callRealMethod();
        }).when(userRepositorySpy).findActiveByIdForShare(userId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> withdrawal = executor.submit(() -> runNamed(WITHDRAWAL_THREAD, this::withdraw));
        await(withdrawalLocked, "탈퇴가 회원 락을 획득하지 못했습니다.");
        Future<String> update = executor.submit(() -> runNamed(UPDATE_THREAD, this::agreeByUpdate));

        try {
            await(updateStarted, "수정이 회원 공유 락 조회를 시작하지 못했습니다.");
            assertThat(update.isDone()).isFalse();
        } finally {
            withdrawalCanCommit.countDown();
        }
        String withdrawalOutcome = withdrawal.get(5, TimeUnit.SECONDS);
        String updateOutcome = update.get(5, TimeUnit.SECONDS);
        shutdown(executor);

        assertThat(withdrawalOutcome).isEqualTo("SUCCESS");
        assertThat(updateOutcome).isEqualTo("UNAUTHORIZED");
        Map<String, Object> post = findPostConsent();
        assertThat(post.get("instagram_upload_agreed")).isEqualTo(false);
        assertThat(post.get("instagram_id")).isNull();
    }

    private String withdraw() {
        try {
            userService.completeWithdrawal(userId, List.of());
            return "SUCCESS";
        } catch (BaseException exception) {
            return exception.getErrorCode().name();
        }
    }

    private String agreeByUpdate() {
        try {
            postCommandService.updatePost(
                    userId,
                    postId,
                    "제목",
                    null,
                    true,
                    "chalkak"
            );
            return "SUCCESS";
        } catch (BaseException exception) {
            return exception.getErrorCode().name();
        }
    }

    private <T> T runNamed(String threadName, Task<T> task) throws Exception {
        Thread.currentThread().setName(threadName);
        return task.run();
    }

    private void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단됐습니다.", exception);
        }
    }

    private void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    private void agreeInstagramUpload() {
        jdbcTemplate.update(
                """
                        UPDATE posts
                        SET instagram_upload_agreed = TRUE, instagram_id = 'chalkak',
                            instagram_agreed_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                postId
        );
    }

    private Map<String, Object> findPostConsent() {
        return jdbcTemplate.queryForMap("""
                SELECT instagram_upload_agreed, instagram_id, instagram_revoked_at
                FROM posts
                WHERE id = ?
                """, postId);
    }

    private Object findUserDeletedAt() {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM users WHERE id = ?",
                Object.class,
                userId
        );
    }

    @FunctionalInterface
    private interface Task<T> {

        T run() throws Exception;
    }
}
