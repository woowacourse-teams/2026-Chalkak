package com.chalkak.backend.filter.api.v1.controller;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class FilterDraftApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/filters";

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
    @DisplayName("이용 가능한 작가는 본문 없이 자신의 빈 필터 초안을 생성한다")
    void createDraft_activeArtist_persistsEmptyDraft() throws Exception {
        // Given
        UUID artistId = createArtist();

        // When
        String response = createDraft(artistId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Then
        String filterId = JsonPath.read(response, "$.filterId");
        Map<String, Object> draft = jdbcTemplate.queryForMap(
                "SELECT artist_id, status, name, description FROM filters WHERE id = ?",
                UUID.fromString(filterId));
        assertThat(draft.get("artist_id")).isEqualTo(artistId);
        assertThat(draft.get("status")).isEqualTo("DRAFT");
        assertThat(draft.get("name")).isNull();
        assertThat(draft.get("description")).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM filter_photos", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("일반 회원의 필터 초안 생성은 거절되고 초안이 저장되지 않는다")
    void createDraft_regularUser_returnsForbiddenWithoutSaving() throws Exception {
        // Given
        UUID userId = createUser(jdbcTemplate);

        // When & Then
        createDraft(userId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("작가만 필터 초안을 만들 수 있습니다."));
        assertNoDrafts();
    }

    @Test
    @DisplayName("정지된 작가의 필터 초안 생성은 거절되고 초안이 저장되지 않는다")
    void createDraft_suspendedArtist_returnsForbiddenWithoutSaving() throws Exception {
        // Given
        UUID artistId = createArtist();
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", artistId);

        // When & Then
        createDraft(artistId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        assertNoDrafts();
    }

    @Test
    @DisplayName("탈퇴한 작가는 기존 토큰으로 필터 초안을 생성할 수 없다")
    void createDraft_withdrawnArtist_returnsUnauthorizedWithoutSaving() throws Exception {
        // Given
        UUID artistId = createArtist();
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                artistId);

        // When & Then
        createDraft(artistId)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        assertNoDrafts();
    }

    @Test
    @DisplayName("인증 없이 필터 초안을 생성할 수 없다")
    void createDraft_unauthenticated_returnsUnauthorizedWithoutSaving() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        assertNoDrafts();
    }

    @Test
    @DisplayName("OpenAPI에 필터 초안 생성의 인증과 본문 없는 요청, 응답 계약이 노출된다")
    void createDraft_openApi_exposesContract() throws Exception {
        // Given
        String operation = "$.paths['" + PATH + "'].post";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[0].accessToken").exists())
                .andExpect(jsonPath(operation + ".requestBody").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['201'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/FilterDraftResponse"))
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists());
    }

    private UUID createArtist() {
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET role = 'ARTIST' WHERE id = ?", userId);
        return userId;
    }

    private ResultActions createDraft(UUID userId) throws Exception {
        String token = accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
        return mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void assertNoDrafts() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM filters", Long.class))
                .isZero();
    }
}
