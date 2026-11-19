package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.support.WithMockLoginUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class PostRegistrationLimitServiceTest extends IntegrationTestSupport {

    private static final String USER_ID_VALUE = "019ad963-213b-734e-a261-60e568c10e45";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID TOPIC_ID = UUID.fromString("019ad963-213b-734e-a261-60e568c10e46");
    private static final Instant NOW = Instant.parse("2026-11-19T14:59:59Z");

    @Autowired
    private PostCommandService commandService;
    @Autowired
    private PostQueryService queryService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private Clock clock;
    @MockitoBean
    private PostImageStorage imageStorage;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        given(clock.instant()).willReturn(NOW);
        jdbc.update(
                """
                        INSERT INTO users (id, email, status, signature_original_storage_key, created_at, updated_at)
                        VALUES (?, 'daily-limit@example.com', 'ACTIVE', 'chalkak/signatures/test/signature.png',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                                """,
                USER_ID);
        jdbc.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at, created_at, updated_at)
                        VALUES (?, '하루 등록 한도', '2026-11-19', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                TOPIC_ID,
                Timestamp.from(NOW.minusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(3600))
        );
        given(imageStorage.toOriginalStorageKey(any())).willAnswer(
                invocation -> "chalkak/posts/test/original/" + invocation.getArgument(0) + ".webp");
        given(imageStorage.toThumbnailStorageKey(any()))
                .willAnswer(invocation -> "chalkak/posts/test/thumbnail/"
                        + invocation.getArgument(0) + ".webp");
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("열 번째 등록은 성공하고 삭제 후 열한 번째 HTTP 요청은 한도 코드로 거절한다")
    void createPost_tenthThenDeleted_rejectsEleventhWithLimitCode() throws Exception {
        // Given
        insertRejectedRegistrations(9);
        UUID uploadId = insertReadyUpload();

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest(uploadId)))
                .andExpect(status().isCreated());
        UUID createdId = jdbc.queryForObject(
                "SELECT id FROM posts WHERE post_image_upload_id = ?",
                UUID.class,
                uploadId
        );
        commandService.deletePost(USER_ID, createdId);
        mockMvc.perform(post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest(insertReadyUpload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_REGISTRATION_LIMIT_REACHED"));
        assertThat(queryService.getMyRegistrationLimit(USER_ID).usedCount()).isEqualTo(10);
        assertThat(queryService.getMyRegistrationLimit(USER_ID).remainingCount()).isZero();
    }

    @Test
    @DisplayName("생성 중 도메인 검증 실패는 업로드 소비와 등록 횟수를 남기지 않는다")
    void createPost_invalidTitle_rollsBackClaimAndRegistration() {
        // Given
        insertRejectedRegistrations(9);
        UUID uploadId = insertReadyUpload();

        // When & Then
        assertThatThrownBy(() -> commandService.createPost(
                USER_ID,
                TOPIC_ID,
                uploadId,
                "제목".repeat(20),
                null,
                false,
                null
        )).isInstanceOf(BusinessException.class);
        assertThat(queryService.getMyRegistrationLimit(USER_ID).usedCount()).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT claimed_at IS NULL FROM post_image_uploads WHERE id = ?",
                Boolean.class,
                uploadId
        )).isTrue();
    }

    @Test
    @DisplayName("저장 뒤 외부 트랜잭션이 롤백돼도 횟수와 업로드 소비가 복구된다")
    void createPost_outerTransactionFails_rollsBackSavedRegistration() {
        // Given
        insertRejectedRegistrations(9);
        UUID uploadId = insertReadyUpload();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // When & Then
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            createPost(uploadId);
            throw new IllegalStateException("등록 트랜잭션 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("등록 트랜잭션 실패");
        assertThat(queryService.getMyRegistrationLimit(USER_ID).usedCount()).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE post_image_upload_id = ?",
                Long.class,
                uploadId
        )).isZero();
    }

    @Test
    @DisplayName("KST 자정 뒤에는 전날 열 번을 제외하고 등록 시각도 같은 날짜에 저장한다")
    void createPost_nextKstDay_resetsQuotaAndPersistsCheckedTime() {
        // Given
        insertRejectedRegistrations(10);
        Instant midnight = Instant.parse("2026-11-19T15:00:00Z");
        given(clock.instant()).willReturn(midnight);

        // When
        PostCreationResult created = createPost(insertReadyUpload());

        // Then
        assertThat(queryService.getMyRegistrationLimit(USER_ID).usedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT created_at FROM posts WHERE id = ?",
                Timestamp.class,
                created.postId()
        )
                .toInstant()).isEqualTo(midnight);
    }

    @Test
    @DisplayName("같은 회원의 재등록이 실제 DB에서 겹쳐도 한도 확인과 저장은 직렬화된다")
    void createPost_concurrentTenthRegistrations_allowsOnlyOne() throws Exception {
        // Given
        insertRejectedRegistrations(9);
        UUID firstUpload = insertReadyUpload();
        UUID secondUpload = insertReadyUpload();
        CountDownLatch firstPassedQuota = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        given(imageStorage.toOriginalStorageKey(firstUpload)).willAnswer(invocation -> {
            firstPassedQuota.countDown();
            assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
            return "chalkak/posts/test/original/" + firstUpload + ".webp";
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> createAndDeleteInTransaction(firstUpload));
            assertThat(firstPassedQuota.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor
                    .submit(() -> createAndDeleteInTransaction(secondUpload));
            // 다른 요청이 회원 행 잠금에서 실제로 기다리는 것을 확인한 뒤 첫 트랜잭션을 진행한다.
            assertThat(awaitUserLockWait()).isTrue();
            releaseFirst.countDown();

            // When & Then
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success",
                            ErrorCode.POST_REGISTRATION_LIMIT_REACHED.name());
            assertThat(queryService.getMyRegistrationLimit(USER_ID).usedCount()).isEqualTo(10);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "BANNED"})
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("정상·정지 회원은 주제가 없어도 본인의 잔여 횟수와 UTC 초기화 시각을 조회한다")
    void getMyRegistrationLimit_existingUser_returnsCountsWithoutOpenTopic(String userStatus)
            throws Exception {
        // Given
        jdbc.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbc.update(
                "UPDATE users SET status = ?::user_status WHERE id = ?",
                userStatus,
                USER_ID
        );

        // When & Then
        mockMvc.perform(get("/api/v1/posts/registration-limit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.remainingCount").value(10))
                .andExpect(jsonPath("$.resetsAt").value("2026-11-19T15:00:00Z"));
    }

    @Test
    @DisplayName("등록 잔여 횟수 조회는 인증 없이 접근할 수 없다")
    void getMyRegistrationLimit_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/registration-limit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("탈퇴 회원은 등록 잔여 횟수를 조회할 수 없다")
    void getMyRegistrationLimit_withdrawnUser_returnsUnauthorized() throws Exception {
        // Given
        jdbc.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", USER_ID);

        // When & Then
        mockMvc.perform(get("/api/v1/posts/registration-limit"))
                .andExpect(status().isUnauthorized());
    }

    private boolean awaitUserLockWait() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM pg_stat_activity
                        WHERE datname = current_database() AND wait_event_type = 'Lock'
                          AND query ILIKE '%users%' AND query ILIKE '%for%update%'
                    )
                    """, Boolean.class);
            if (Boolean.TRUE.equals(waiting)) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private String createAndDeleteInTransaction(UUID uploadId) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                PostCreationResult created = createPost(uploadId);
                commandService.deletePost(USER_ID, created.postId());
            });
            return "success";
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }

    private PostCreationResult createPost(UUID uploadId) {
        return commandService.createPost(
                USER_ID,
                TOPIC_ID,
                uploadId,
                null,
                null,
                false,
                null
        );
    }

    private UUID insertReadyUpload() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO post_image_uploads (id, user_id, status, image_metadata, expires_at)
                        VALUES (?, ?, 'READY', '{}'::jsonb, ?)
                        """,
                id,
                USER_ID,
                Timestamp.from(NOW.plusSeconds(3600))
        );
        return id;
    }

    private void insertRejectedRegistrations(int count) {
        for (int index = 0; index < count; index++) {
            UUID photoId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO photos (id, original_storage_key) VALUES (?, ?)",
                    photoId,
                    "chalkak/posts/test/history/" + photoId + ".webp"
            );
            jdbc.update(
                    """
                            INSERT INTO posts (user_id, topic_id, photo_id, moderation_status, created_at)
                            VALUES (?, ?, ?, 'REJECTED', ?)
                            """,
                    USER_ID,
                    TOPIC_ID,
                    photoId,
                    Timestamp.from(NOW)
            );
        }
    }

    private String createRequest(UUID uploadId) {
        return """
                {"topicId":"%s", "photoUploadId":"%s"}
                """.formatted(TOPIC_ID, uploadId);
    }
}
