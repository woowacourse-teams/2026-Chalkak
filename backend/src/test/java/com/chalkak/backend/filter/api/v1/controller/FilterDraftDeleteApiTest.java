package com.chalkak.backend.filter.api.v1.controller;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.util.UUID;
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

@AutoConfigureMockMvc
class FilterDraftDeleteApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/filters/{filterId}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

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
    @DisplayName("초안을 삭제하면 조회와 수정에서 제외되고 다시 삭제해도 처음 삭제 시각을 유지한다")
    void deleteDraft_ownedDraft_hidesItAndPreservesFirstDeletionTime() throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        String token = bearerToken(artistId);

        // When & Then
        deleteDraft(artistId, filterId).andExpect(status().isNoContent());
        Timestamp firstDeletedAt = findDeletedAt(filterId);
        assertThat(firstDeletedAt).isNotNull();
        mockMvc.perform(get(PATH, filterId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/{userId}/filters", artistId)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters").isEmpty());
        mockMvc.perform(patch(PATH, filterId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"삭제한 초안"}
                        """))
                .andExpect(status().isNotFound());
        deleteDraft(artistId, filterId).andExpect(status().isNoContent());
        assertThat(findDeletedAt(filterId)).isEqualTo(firstDeletedAt);
    }

    @Test
    @DisplayName("초안을 삭제해도 이미 연결에 사용한 업로드는 새 초안에 다시 연결할 수 없다")
    void deleteDraft_claimedPhoto_keepsUploadUnavailable() throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        UUID otherDraftId = createFilter(artistId, "DRAFT");
        UUID uploadId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO post_image_uploads (user_id, status, expires_at, image_metadata)
                        VALUES (?, 'READY', CURRENT_TIMESTAMP + INTERVAL '1 hour', '{}'::jsonb)
                        RETURNING id
                        """,
                UUID.class,
                artistId
        );
        String token = bearerToken(artistId);
        String photos = """
                {"uploadIds":["%s"]}
                """.formatted(uploadId);
        mockMvc.perform(patch(PATH, filterId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(photos))
                .andExpect(status().isNoContent());

        // When & Then
        deleteDraft(artistId, filterId).andExpect(status().isNoContent());
        mockMvc.perform(patch(PATH, otherDraftId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(photos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 사용된 사진입니다."));
    }

    @Test
    @DisplayName("다른 작가와 권한이 회수되거나 정지된 작성자는 삭제할 수 없다")
    void deleteDraft_withoutArtistPermission_leavesDraftUntouched() throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");

        // When & Then
        deleteDraft(createArtist(), filterId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 필터가 아닙니다."));
        jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE id = ?", artistId);
        deleteDraft(artistId, filterId).andExpect(status().isForbidden());
        jdbcTemplate.update("UPDATE users SET role = 'ARTIST', status = 'BANNED' WHERE id = ?",
                artistId);
        deleteDraft(artistId, filterId).andExpect(status().isForbidden());
        assertThat(findDeletedAt(filterId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "PRIVATE"})
    @DisplayName("한 번 공개한 필터는 현재 상태가 공개든 비공개든 삭제할 수 없다")
    void deleteDraft_nonDraft_returnsBadRequest(String filterStatus) throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, filterStatus);

        // When & Then
        deleteDraft(artistId, filterId).andExpect(status().isBadRequest());
        assertThat(findDeletedAt(filterId)).isNull();
    }

    @Test
    @DisplayName("없는 필터는 404, 잘못된 필터 ID는 400을 반환한다")
    void deleteDraft_missingOrInvalidId_returnsExpectedStatus() throws Exception {
        // Given
        UUID artistId = createArtist();

        // When & Then
        deleteDraft(artistId, UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(delete(PATH, "invalid-id")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(artistId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인과 탈퇴한 회원의 삭제 요청은 거절한다")
    void deleteDraft_withoutExistingLogin_returnsUnauthorized() throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");

        // When & Then
        mockMvc.perform(delete(PATH, filterId)).andExpect(status().isUnauthorized());
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                artistId);
        deleteDraft(artistId, filterId).andExpect(status().isUnauthorized());
        assertThat(findDeletedAt(filterId)).isNull();
    }

    @Test
    @DisplayName("OpenAPI에 초안 삭제의 토큰 인증과 204 응답이 표시된다")
    void deleteDraft_openApi_exposesContract() throws Exception {
        // Given
        String operation = "$.paths['" + PATH + "'].delete";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[0].accessToken").exists())
                .andExpect(jsonPath(operation + ".requestBody").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['204']").exists());
    }

    private UUID createArtist() {
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET role = 'ARTIST' WHERE id = ?", userId);
        return userId;
    }

    private UUID createFilter(UUID artistId, String filterStatus) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO filters (artist_id, status, published_at)
                        VALUES (?, ?, CASE WHEN ? = 'DRAFT' THEN NULL ELSE CURRENT_TIMESTAMP END)
                        RETURNING id
                        """,
                UUID.class,
                artistId,
                filterStatus,
                filterStatus
        );
    }

    private Timestamp findDeletedAt(UUID filterId) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM filters WHERE id = ?",
                Timestamp.class,
                filterId
        );
    }

    private ResultActions deleteDraft(UUID userId, UUID filterId) throws Exception {
        return mockMvc.perform(delete(PATH, filterId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)));
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }
}
