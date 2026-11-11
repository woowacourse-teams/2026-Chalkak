package com.chalkak.backend.user.api.v1.controller;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.support.AuthorPostFixtures;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class ArtistEligibilityApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/users/me/artist-eligibility";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @ParameterizedTest
    @CsvSource({"99,false", "100,true", "101,true"})
    @DisplayName("공개 게시물의 좋아요 합계가 100개 이상이면 신청할 수 있다")
    void getArtistEligibility_likeCountBoundary_returnsEligibility(int likeCount, boolean canApply)
            throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        UUID first = createApprovedPost(userId, 1);
        UUID second = createApprovedPost(userId, 2);
        updateLikeCount(first, 40);
        updateLikeCount(second, likeCount - 40);

        // When & Then
        getEligibility(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedLikeCount").value(likeCount))
                .andExpect(jsonPath("$.requiredLikeCount").value(100))
                .andExpect(jsonPath("$.canApply").value(canApply));
    }

    @Test
    @DisplayName("미승인·삭제·다른 회원 게시물의 좋아요는 합산하지 않는다")
    void getArtistEligibility_nonPublicOrOtherAuthorPosts_excludesTheirLikes() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        UUID approved = createApprovedPost(userId, 1);
        UUID pending = createPost(
                userId,
                2,
                ModerationStatus.PENDING
        );
        UUID deletedPost = createApprovedPost(userId, 3);
        UUID deletedTopic = createApprovedPost(userId, 4);
        UUID deletedPhoto = createApprovedPost(userId, 5);
        UUID otherAuthor = createApprovedPost(createUser(jdbcTemplate), 6);
        jdbcTemplate.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                deletedPost);
        jdbcTemplate.update("""
                UPDATE topics SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT topic_id FROM posts WHERE id = ?)
                """, deletedTopic);
        jdbcTemplate.update("""
                UPDATE photos SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT photo_id FROM posts WHERE id = ?)
                """, deletedPhoto);
        updateLikeCount(approved, 30);
        updateLikeCount(pending, 100);
        updateLikeCount(deletedPost, 100);
        updateLikeCount(deletedTopic, 100);
        updateLikeCount(deletedPhoto, 100);
        updateLikeCount(otherAuthor, 100);

        // When & Then
        getEligibility(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedLikeCount").value(30))
                .andExpect(jsonPath("$.canApply").value(false));
    }

    @Test
    @DisplayName("공개 게시물이 없으면 좋아요 합계를 0으로 조회한다")
    void getArtistEligibility_noPublicPosts_returnsZero() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);

        // When & Then
        getEligibility(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedLikeCount").value(0))
                .andExpect(jsonPath("$.canApply").value(false));
    }

    @Test
    @DisplayName("공개 게시물에 좋아요 카운터가 없으면 좋아요 합계를 0으로 조회한다")
    void getArtistEligibility_noLikeCounter_returnsZero() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        UUID postId = createApprovedPost(userId, 1);
        jdbcTemplate.update("DELETE FROM post_like_counts WHERE post_id = ?", postId);

        // When & Then
        getEligibility(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedLikeCount").value(0))
                .andExpect(jsonPath("$.canApply").value(false));
    }

    @ParameterizedTest
    @CsvSource({"BANNED,USER", "ACTIVE,ARTIST"})
    @DisplayName("정지 회원과 기존 작가는 좋아요 기준을 충족해도 신규 신청이 불가능하다")
    void getArtistEligibility_suspendedOrAlreadyArtist_returnsCannotApply(
            String userStatus, String role
    ) throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update(
                "UPDATE users SET status = ?::user_status, role = ? WHERE id = ?",
                userStatus,
                role,
                userId
        );
        UUID postId = createApprovedPost(userId, 1);
        updateLikeCount(postId, 100);

        // When & Then
        getEligibility(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedLikeCount").value(100))
                .andExpect(jsonPath("$.canApply").value(false));
    }

    @Test
    @DisplayName("미인증 요청은 작가 신청 자격을 조회할 수 없다")
    void getArtistEligibility_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴한 회원은 남은 토큰으로 작가 신청 자격을 조회할 수 없다")
    void getArtistEligibility_withdrawnUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);

        // When & Then
        getEligibility(userId).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사용자 OpenAPI 문서에 작가 신청 자격 조회의 인증과 응답 필드가 나온다")
    void userApiDocs_getArtistEligibility_exposesContract() throws Exception {
        // Given
        String operation = "$.paths['" + PATH + "'].get";
        String properties = "$.components.schemas.ArtistEligibilityResponse.properties";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[0].accessToken").exists())
                .andExpect(jsonPath(operation + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/ArtistEligibilityResponse"))
                .andExpect(jsonPath(properties + ".keys()")
                        .value(containsInAnyOrder(
                                "receivedLikeCount",
                                "requiredLikeCount",
                                "canApply"
                        )));
    }

    private ResultActions getEligibility(UUID userId) throws Exception {
        String token = accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
        return mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private UUID createApprovedPost(UUID authorId, int day) {
        return createPost(
                authorId,
                day,
                ModerationStatus.APPROVED
        );
    }

    private UUID createPost(
            UUID authorId,
            int day,
            ModerationStatus status
    ) {
        return AuthorPostFixtures.createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        5,
                        day
                ),
                status
        );
    }

    private void updateLikeCount(UUID postId, int likeCount) {
        jdbcTemplate.update(
                "UPDATE post_like_counts SET like_count = ? WHERE post_id = ?",
                likeCount,
                postId
        );
    }
}
