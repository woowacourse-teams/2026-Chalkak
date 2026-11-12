package com.chalkak.backend.user.api.v1.controller;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.support.AuthorPostFixtures;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserRole;
import com.chalkak.backend.user.service.UserProfileResult;
import com.chalkak.backend.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class ArtistApplicationApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/users/me/artist";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private UserService userService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        databaseCleaner.clean();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("좋아요 100개를 받은 일반 회원은 본문 없이 신청해 작가가 된다")
    void applyForArtist_exactLikeThreshold_savesArtistRole() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        createApprovedPostWithLikes(userId, 100);

        // When & Then
        apply(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ARTIST"));
        assertThat(findRole(userId)).isEqualTo("ARTIST");
    }

    @Test
    @DisplayName("좋아요 99개를 받은 일반 회원의 신청은 거절되고 역할은 유지된다")
    void applyForArtist_belowLikeThreshold_rejectsWithoutSaving() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        createApprovedPostWithLikes(userId, 99);

        // When & Then
        apply(userId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("작가 신청에 필요한 좋아요 수를 충족하지 않았습니다."));
        assertThat(findRole(userId)).isEqualTo("USER");
    }

    @Test
    @DisplayName("자격 조회 뒤 게시물이 삭제되면 신청 시점에 다시 합산해 거절한다")
    void applyForArtist_postDeletedAfterEligibilityCheck_rechecksLikes() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        UUID postId = createApprovedPostWithLikes(userId, 100);
        mockMvc.perform(get("/api/v1/users/me/artist-eligibility")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canApply").value(true));
        jdbcTemplate.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", postId);

        // When & Then
        apply(userId).andExpect(status().isBadRequest());
        assertThat(findRole(userId)).isEqualTo("USER");
    }

    @Test
    @DisplayName("이미 작가인 회원은 작가 신청을 다시 할 수 없다")
    void applyForArtist_alreadyArtist_returnsStateChanged() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET role = 'ARTIST' WHERE id = ?", userId);

        // When & Then
        apply(userId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_STATE_CHANGED"));
        assertThat(findRole(userId)).isEqualTo("ARTIST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ARTIST"})
    @DisplayName("정지 회원은 역할과 관계없이 작가 신청을 할 수 없다")
    void applyForArtist_suspendedUser_returnsForbidden(String role) throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update(
                "UPDATE users SET status = 'BANNED', role = ? WHERE id = ?",
                role,
                userId
        );
        createApprovedPostWithLikes(userId, 100);

        // When & Then
        apply(userId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        assertThat(findRole(userId)).isEqualTo(role);
    }

    @Test
    @DisplayName("탈퇴 회원은 남은 토큰으로도 작가 신청을 할 수 없다")
    void applyForArtist_withdrawnUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);

        // When & Then
        apply(userId)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("미인증 회원은 작가 신청을 할 수 없다")
    void applyForArtist_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("겹친 작가 신청은 먼저 처리된 요청만 역할을 바꾼다")
    void applyForArtist_concurrentRequests_changesRoleOnce() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        createApprovedPostWithLikes(userId, 100);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch firstCanCommit = new CountDownLatch(1);

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<UserProfileResult> first = executor.submit(() -> transaction.execute(status -> {
                UserProfileResult result = userService.applyForArtist(userId);
                entityManager.flush();
                firstSaved.countDown();
                await(firstCanCommit);
                return result;
            }));
            Future<ResultActions> second;
            try {
                await(firstSaved);
                second = executor.submit(() -> {
                    secondStarted.countDown();
                    return apply(userId);
                });
                await(secondStarted);
                assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            } finally {
                firstCanCommit.countDown();
            }

            // Then
            assertThat(first.get(5, TimeUnit.SECONDS))
                    .isEqualTo(new UserProfileResult(UserRole.ARTIST));
            second.get(5, TimeUnit.SECONDS)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_STATE_CHANGED"));
            assertThat(findRole(userId)).isEqualTo("ARTIST");
        }
    }

    @Test
    @DisplayName("OpenAPI에서 본문 없는 작가 신청의 인증과 응답을 확인할 수 있다")
    void userApiDocs_applyForArtist_exposesContract() throws Exception {
        // Given
        String operation = "$.paths['" + PATH + "'].post";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[0].accessToken").exists())
                .andExpect(jsonPath(operation + ".requestBody").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/UserProfileResponse"))
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists());
    }

    private ResultActions apply(UUID userId) throws Exception {
        return mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)));
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }

    private UUID createApprovedPostWithLikes(UUID userId, int likeCount) {
        UUID postId = AuthorPostFixtures.createPost(
                jdbcTemplate,
                userId,
                LocalDate.of(
                        2026,
                        5,
                        1
                ),
                ModerationStatus.APPROVED
        );
        jdbcTemplate.update(
                "UPDATE post_like_counts SET like_count = ? WHERE post_id = ?",
                likeCount,
                postId
        );
        return postId;
    }

    private String findRole(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?",
                String.class,
                userId
        );
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
