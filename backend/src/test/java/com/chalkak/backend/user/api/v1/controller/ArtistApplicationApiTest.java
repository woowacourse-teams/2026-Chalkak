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
import java.sql.Timestamp;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class ArtistApplicationApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/users/me/artist";
    private static final String AGREED = """
            {"artistTermsAgreed":true}
            """;

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
    @DisplayName("좋아요 100개를 받은 일반 회원이 동의하면 작가 역할과 동의 시각을 저장한다")
    void applyForArtist_exactLikeThreshold_savesRoleAndAgreement() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        createApprovedPostWithLikes(userId, 100);

        // When & Then
        apply(userId, AGREED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andExpect(jsonPath("$.artistTermsAgreed").value(true));
        assertThat(findRole(userId)).isEqualTo("ARTIST");
        assertThat(findAgreedAt(userId)).isNotNull();
    }

    @Test
    @DisplayName("좋아요 99개를 받은 일반 회원은 역할과 동의 상태가 바뀌지 않는다")
    void applyForArtist_belowLikeThreshold_rejectsWithoutSaving() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        createApprovedPostWithLikes(userId, 99);

        // When & Then
        apply(userId, AGREED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("작가 신청에 필요한 좋아요 수를 충족하지 않았습니다."));
        assertThat(findRole(userId)).isEqualTo("USER");
        assertThat(findAgreedAt(userId)).isNull();
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
        apply(userId, AGREED).andExpect(status().isBadRequest());
        assertThat(findRole(userId)).isEqualTo("USER");
        assertThat(findAgreedAt(userId)).isNull();
    }

    @Test
    @DisplayName("이미 작가인 미동의 회원은 좋아요가 없어도 동의 시각을 저장할 수 있다")
    void applyForArtist_artistWithoutAgreement_recordsFirstAgreement() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET role = 'ARTIST' WHERE id = ?", userId);

        // When & Then
        apply(userId, AGREED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andExpect(jsonPath("$.artistTermsAgreed").value(true));
        assertThat(findAgreedAt(userId)).isNotNull();
    }

    @Test
    @DisplayName("이미 동의한 작가의 반복 요청은 최초 동의 시각을 유지한다")
    void applyForArtist_alreadyAgreed_preservesFirstAgreement() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("""
                UPDATE users SET role = 'ARTIST', artist_agreed_at = '2026-05-01T00:00:00Z'
                WHERE id = ?
                """, userId);
        Timestamp firstAgreement = findAgreedAt(userId);

        // When & Then
        apply(userId, AGREED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistTermsAgreed").value(true));
        assertThat(findAgreedAt(userId)).isEqualTo(firstAgreement);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"artistTermsAgreed\":null}",
            "{\"artistTermsAgreed\":false}"})
    @DisplayName("원작 확약 동의가 없으면 좋아요 기준을 충족해도 신청을 거절한다")
    void applyForArtist_missingOrRejectedAgreement_rejectsWithoutSaving(String body)
            throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        createApprovedPostWithLikes(userId, 100);

        // When & Then
        apply(userId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        assertThat(findRole(userId)).isEqualTo("USER");
        assertThat(findAgreedAt(userId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ARTIST"})
    @DisplayName("정지 회원은 일반 회원과 작가 모두 원작 확약을 기록할 수 없다")
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
        apply(userId, AGREED)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        assertThat(findRole(userId)).isEqualTo(role);
        assertThat(findAgreedAt(userId)).isNull();
    }

    @Test
    @DisplayName("탈퇴 회원은 남은 토큰으로도 작가 신청을 할 수 없다")
    void applyForArtist_withdrawnUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);

        // When & Then
        apply(userId, AGREED)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("미인증 회원은 작가 신청을 할 수 없다")
    void applyForArtist_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(AGREED))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("겹친 작가 신청은 첫 요청이 저장한 역할과 최초 동의 시각을 공유한다")
    void applyForArtist_concurrentRequests_preservesFirstAgreement() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        createApprovedPostWithLikes(userId, 100);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch firstCanCommit = new CountDownLatch(1);

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Timestamp> first = executor.submit(() -> transaction.execute(status -> {
                userService.applyForArtist(userId);
                entityManager.flush();
                Timestamp agreedAt = findAgreedAt(userId);
                firstSaved.countDown();
                await(firstCanCommit);
                return agreedAt;
            }));
            Future<UserProfileResult> second;
            try {
                await(firstSaved);
                second = executor.submit(() -> {
                    secondStarted.countDown();
                    return userService.applyForArtist(userId);
                });
                await(secondStarted);
                assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            } finally {
                firstCanCommit.countDown();
            }

            // Then
            Timestamp firstAgreement = first.get(5, TimeUnit.SECONDS);
            assertThat(firstAgreement).isNotNull();
            assertThat(second.get(5, TimeUnit.SECONDS))
                    .isEqualTo(new UserProfileResult(UserRole.ARTIST, true));
            assertThat(findAgreedAt(userId)).isEqualTo(firstAgreement);
        }
    }

    @Test
    @DisplayName("OpenAPI에서 작가 신청의 인증, 필수 동의 필드와 응답을 확인할 수 있다")
    void userApiDocs_applyForArtist_exposesContract() throws Exception {
        // Given
        String operation = "$.paths['" + PATH + "'].post";
        String requestSchema = "$.components.schemas.ArtistApplicationRequest";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[0].accessToken").exists())
                .andExpect(jsonPath(
                        operation + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ArtistApplicationRequest"))
                .andExpect(jsonPath(requestSchema + ".required[0]").value("artistTermsAgreed"))
                .andExpect(jsonPath(requestSchema + ".properties.artistTermsAgreed.type")
                        .value("boolean"))
                .andExpect(jsonPath(operation + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/UserProfileResponse"))
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists());
    }

    private ResultActions apply(UUID userId, String body) throws Exception {
        return mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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

    private Timestamp findAgreedAt(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT artist_agreed_at FROM users WHERE id = ?",
                Timestamp.class,
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
