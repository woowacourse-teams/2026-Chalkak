package com.chalkak.backend.filter.api.v1.controller;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class FilterDraftUpdateApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/filters/{filterId}";
    private static final String UPDATE_NAME = """
            {"name":"수정한 이름"}
            """;

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
    @DisplayName("보낸 텍스트만 바꾸며 공백을 보내면 값을 지운다")
    void updateDraft_partialText_updatesOnlyProvidedFields() throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        UUID uploadId = createUpload(artistId, "READY");
        updateDraft(
                artistId,
                filterId,
                photos(uploadId)
        ).andExpect(status().isNoContent());

        // When & Then
        updateDraft(
                artistId,
                filterId,
                """
                        {"name":"  수정한 이름  ","description":null}
                        """
        ).andExpect(status().isNoContent());
        getDraft(artistId, filterId)
                .andExpect(jsonPath("$.name").value("수정한 이름"))
                .andExpect(jsonPath("$.description").value("기존 설명"))
                .andExpect(jsonPath("$.photos[0].uploadId").value(uploadId.toString()));

        updateDraft(
                artistId,
                filterId,
                """
                        {"description":"   "}
                        """
        ).andExpect(status().isNoContent());
        getDraft(artistId, filterId)
                .andExpect(jsonPath("$.name").value("수정한 이름"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    @DisplayName("사진 목록을 순서대로 교체하며 이미 연결한 사진의 순서를 다시 바꿀 수 있다")
    void updateDraft_replacePhotos_preservesOrderAndClaims() throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        UUID ready = createUpload(artistId, "READY");
        UUID processing = createUpload(artistId, "ISSUED");
        UUID replacement = createUpload(artistId, "READY");
        updateDraft(
                artistId,
                filterId,
                photos(ready, processing)
        ).andExpect(status().isNoContent());
        var firstClaimedAt = getClaimedAt(ready);

        // When & Then
        updateDraft(
                artistId,
                filterId,
                photos(processing, ready)
        ).andExpect(status().isNoContent());
        getDraft(artistId, filterId)
                .andExpect(jsonPath("$.photos[0].uploadId").value(processing.toString()))
                .andExpect(jsonPath("$.photos[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.photos[1].uploadId").value(ready.toString()));
        updateDraft(
                artistId,
                filterId,
                photos(replacement, ready)
        ).andExpect(status().isNoContent());
        assertThat(getPhotoIds(filterId)).containsExactly(replacement, ready);
        assertThat(getClaimedAt(ready)).isNotNull().isEqualTo(firstClaimedAt);
        assertThat(getClaimedAt(replacement)).isNotNull();
        assertThat(getClaimedAt(processing)).isNotNull();
    }

    @Test
    @DisplayName("다른 회원의 사진 때문에 수정이 실패하면 기존 내용을 보존하고 새 사진의 선점을 되돌린다")
    void updateDraft_otherMembersUpload_rollsBackEntireUpdate() throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        UUID current = createUpload(artistId, "READY");
        UUID valid = createUpload(artistId, "READY");
        UUID other = createUpload(createUser(jdbcTemplate), "READY");
        assertThat(valid.compareTo(other)).isNegative();
        updateDraft(
                artistId,
                filterId,
                photos(current)
        ).andExpect(status().isNoContent());

        // When
        updateDraft(
                artistId,
                filterId,
                """
                        {"name":"새 이름","uploadIds":["%s","%s"]}
                        """.formatted(valid, other)
        ).andExpect(status().isNotFound());

        // Then
        getDraft(artistId, filterId).andExpect(jsonPath("$.name").value("기존 이름"));
        assertThat(getPhotoIds(filterId)).containsExactly(current);
        assertThat(getClaimedAt(valid)).isNull();
    }

    @Test
    @DisplayName("두 초안이 같은 사진을 동시에 연결하면 한쪽만 성공한다")
    void updateDraft_concurrentPhotoClaims_allowsOnlyOneFilter() throws Exception {
        // Given
        UUID artistId = createArtist();
        UUID first = createFilter(artistId, "DRAFT");
        UUID second = createFilter(artistId, "DRAFT");
        UUID uploadId = createUpload(artistId, "READY");
        CyclicBarrier start = new CyclicBarrier(2);

        // When & Then
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> updateAfterBarrier(
                    start,
                    artistId,
                    first,
                    uploadId
            ));
            var secondResult = executor.submit(() -> updateAfterBarrier(
                    start,
                    artistId,
                    second,
                    uploadId
            ));
            assertThat(List.of(firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 400);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM filter_photos WHERE upload_id = ?",
                Integer.class,
                uploadId
        )).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "PRIVATE"})
    @DisplayName("공개했거나 비공개로 내린 필터는 수정할 수 없다")
    void updateDraft_nonDraft_returnsBadRequest(String filterStatus) throws Exception {
        UUID artistId = createArtist();
        updateDraft(
                artistId,
                createFilter(artistId, filterStatus),
                UPDATE_NAME
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 작가, 권한이 회수된 작성자, 정지된 작성자의 수정은 거절한다")
    void updateDraft_withoutArtistPermission_returnsForbidden() throws Exception {
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        updateDraft(
                createArtist(),
                filterId,
                UPDATE_NAME
        ).andExpect(status().isForbidden());

        jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE id = ?", artistId);
        updateDraft(
                artistId,
                filterId,
                UPDATE_NAME
        ).andExpect(status().isForbidden());

        jdbcTemplate.update("UPDATE users SET role = 'ARTIST', status = 'BANNED' WHERE id = ?",
                artistId);
        updateDraft(
                artistId,
                filterId,
                UPDATE_NAME
        ).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("이미 사용한 사진, 처리 실패 사진, 만료된 사진은 새로 연결할 수 없다")
    void updateDraft_invalidUploadState_returnsBadRequest() throws Exception {
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        UUID used = createUpload(artistId, "READY");
        UUID rejected = createUpload(artistId, "REJECTED");
        UUID expired = createUpload(artistId, "READY");
        jdbcTemplate.update(
                "UPDATE post_image_uploads SET claimed_at = CURRENT_TIMESTAMP WHERE id = ?", used);
        jdbcTemplate.update(
                "UPDATE post_image_uploads SET expires_at = CURRENT_TIMESTAMP WHERE id = ?",
                expired);

        List<UUID> invalidUploads = List.of(
                used,
                rejected,
                expired
        );
        for (UUID uploadId : invalidUploads) {
            updateDraft(
                    artistId,
                    filterId,
                    photos(uploadId)
            ).andExpect(status().isBadRequest());
        }
        assertThat(getPhotoIds(filterId)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"uploadIds\":[]}", "{\"uploadIds\":[null]}",
            "{\"uploadIds\":[\"1-1-1-1-1\"]}"})
    @DisplayName("수정할 값이 없거나 업로드 목록 형식이 잘못되면 거절한다")
    void updateDraft_invalidBody_returnsBadRequest(String body) throws Exception {
        UUID artistId = createArtist();
        updateDraft(
                artistId,
                createFilter(artistId, "DRAFT"),
                body
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사진이 중복되거나 6장 이상이면 거절한다")
    void updateDraft_invalidPhotoCountOrDuplicate_returnsBadRequest() throws Exception {
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        UUID uploadId = createUpload(artistId, "READY");
        updateDraft(
                artistId,
                filterId,
                photos(uploadId, uploadId)
        ).andExpect(status().isBadRequest());
        updateDraft(
                artistId,
                filterId,
                photos(
                        uploadId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        ).andExpect(status().isBadRequest());
        assertThat(getClaimedAt(uploadId)).isNull();
    }

    @Test
    @DisplayName("이름은 공백 제거 후 코드 포인트 30자까지 저장하고 31자는 거절한다")
    void updateDraft_nameLengthBoundary_countsCodePoints() throws Exception {
        UUID artistId = createArtist();
        UUID filterId = createFilter(artistId, "DRAFT");
        String name = "🌲".repeat(30);
        updateDraft(
                artistId,
                filterId,
                "{\"name\":\" %s \"}".formatted(name)
        ).andExpect(status().isNoContent());
        updateDraft(
                artistId,
                filterId,
                "{\"name\":\"%s!\"}".formatted(name)
        ).andExpect(status().isBadRequest());
        getDraft(artistId, filterId).andExpect(jsonPath("$.name").value(name));
    }

    @Test
    @DisplayName("OpenAPI에 필터 수정의 토큰 인증, 요청과 204 응답 계약이 노출된다")
    void updateDraft_openApi_exposesContract() throws Exception {
        String operation = "$.paths['" + PATH + "'].patch";
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[0].accessToken").exists())
                .andExpect(jsonPath(
                        operation + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/FilterDraftUpdateRequest"))
                .andExpect(jsonPath(operation + ".responses['204']").exists());
    }

    private int updateAfterBarrier(
            CyclicBarrier start,
            UUID artistId,
            UUID filterId,
            UUID uploadId
    ) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return updateDraft(
                artistId,
                filterId,
                photos(uploadId)
        ).andReturn().getResponse().getStatus();
    }

    private UUID createArtist() {
        UUID userId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET role = 'ARTIST' WHERE id = ?", userId);
        return userId;
    }

    private UUID createFilter(UUID artistId, String filterStatus) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO filters (artist_id, name, description, status, published_at)
                        VALUES (?, '기존 이름', '기존 설명', ?, CASE WHEN ? = 'DRAFT' THEN NULL ELSE CURRENT_TIMESTAMP END)
                        RETURNING id
                        """,
                UUID.class,
                artistId,
                filterStatus,
                filterStatus
        );
    }

    private UUID createUpload(UUID userId, String uploadStatus) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post_image_uploads (user_id, status, expires_at, image_metadata, rejection_reason)
                        VALUES (?, CAST(? AS post_image_upload_status), CURRENT_TIMESTAMP + INTERVAL '1 hour',
                            CASE WHEN ? = 'READY' THEN '{}'::jsonb END, CASE WHEN ? = 'REJECTED' THEN 'CORRUPTED_IMAGE' END)
                        RETURNING id
                        """,
                UUID.class,
                userId,
                uploadStatus,
                uploadStatus,
                uploadStatus
        );
    }

    private List<UUID> getPhotoIds(UUID filterId) {
        return jdbcTemplate.queryForList(
                "SELECT upload_id FROM filter_photos WHERE filter_id = ? ORDER BY sort_order",
                UUID.class,
                filterId
        );
    }

    private Timestamp getClaimedAt(UUID uploadId) {
        return jdbcTemplate.queryForObject(
                "SELECT claimed_at FROM post_image_uploads WHERE id = ?",
                Timestamp.class,
                uploadId
        );
    }

    private String photos(UUID... uploadIds) {
        return "{\"uploadIds\":%s}".formatted(List.of(uploadIds).stream()
                .map(id -> "\"" + id + "\"")
                .toList());
    }

    private ResultActions updateDraft(
            UUID viewerId,
            UUID filterId,
            String body
    ) throws Exception {
        return mockMvc.perform(patch(PATH, filterId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getDraft(UUID viewerId, UUID filterId) throws Exception {
        return mockMvc
                .perform(get(PATH, filterId).header(HttpHeaders.AUTHORIZATION,
                        bearerToken(viewerId)))
                .andExpect(status().isOk());
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }
}
