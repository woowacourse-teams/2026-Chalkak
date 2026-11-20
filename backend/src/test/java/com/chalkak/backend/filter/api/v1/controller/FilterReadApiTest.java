package com.chalkak.backend.filter.api.v1.controller;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.filter.domain.FilterStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class FilterReadApiTest extends IntegrationTestSupport {

    private static final String LIST_PATH = "/api/v1/users/{userId}/filters";
    private static final String DETAIL_PATH = "/api/v1/filters/{filterId}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("본인 목록은 공개·초안·비공개를 최신순으로 보여주고 삭제된 필터와 남의 필터를 제외한다")
    void getAuthorFilters_owner_returnsAllOwnedStatusesInOrder() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID other = createUser(jdbcTemplate);
        UUID privateId = createFilter(owner, FilterStatus.PRIVATE);
        UUID draftId = createFilter(owner, FilterStatus.DRAFT);
        UUID publicId = createFilter(owner, FilterStatus.PUBLIC);
        UUID deletedId = createFilter(owner, FilterStatus.DRAFT);
        createFilter(other, FilterStatus.PUBLIC);
        jdbcTemplate.update(
                "UPDATE filters SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 days' WHERE id = ?",
                privateId);
        jdbcTemplate.update(
                "UPDATE filters SET created_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = ?",
                draftId);
        jdbcTemplate.update("UPDATE filters SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                deletedId);

        // When & Then
        performAs(owner, get(LIST_PATH, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.filters", hasSize(3)))
                .andExpect(jsonPath("$.filters[0].filterId").value(publicId.toString()))
                .andExpect(jsonPath("$.filters[0].status").value("PUBLIC"))
                .andExpect(jsonPath("$.filters[1].filterId").value(draftId.toString()))
                .andExpect(jsonPath("$.filters[1].status").value("DRAFT"))
                .andExpect(jsonPath("$.filters[2].filterId").value(privateId.toString()))
                .andExpect(jsonPath("$.filters[2].status").value("PRIVATE"));
    }

    @Test
    @DisplayName("목록은 같은 시각의 필터도 ID로 순서를 정하고 다음 페이지 여부를 반환한다")
    void getAuthorFilters_pageSizeOne_preservesOrderAndHasNext() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID first = createFilter(owner, FilterStatus.DRAFT);
        UUID second = createFilter(owner, FilterStatus.DRAFT);
        List<UUID> expectedOrder = List.of(first, second).stream().sorted(Comparator.reverseOrder())
                .toList();

        // When & Then
        performAs(owner, get(LIST_PATH, owner).param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.filters", hasSize(1)))
                .andExpect(jsonPath("$.filters[0].filterId")
                        .value(expectedOrder.getFirst().toString()));
        performAs(owner, get(LIST_PATH, owner).param("page", "2").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.filters", hasSize(1)))
                .andExpect(jsonPath("$.filters[0].filterId")
                        .value(expectedOrder.getLast().toString()));
    }

    @Test
    @DisplayName("필터가 없는 일반 회원은 자신의 빈 목록을 조회할 수 있다")
    void getAuthorFilters_noOwnedFilters_returnsEmptyList() throws Exception {
        // Given
        UUID viewer = createUser(jdbcTemplate);

        // When & Then
        performAs(viewer, get(LIST_PATH, viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("다른 회원의 필터 목록에는 공개 필터만 포함된다")
    void getAuthorFilters_otherMember_returnsOnlyPublicFilters() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID viewer = createUser(jdbcTemplate);
        UUID publicId = createFilter(owner, FilterStatus.PUBLIC);
        createFilter(owner, FilterStatus.DRAFT);
        createFilter(owner, FilterStatus.PRIVATE);

        // When & Then
        performAs(viewer, get(LIST_PATH, owner).param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.filters", hasSize(1)))
                .andExpect(jsonPath("$.filters[0].filterId").value(publicId.toString()))
                .andExpect(jsonPath("$.filters[0].status").value("PUBLIC"));
    }

    @Test
    @DisplayName("본인 상세는 사진 순서와 처리 상태를 반환하고 READY 사진에만 이미지 URL을 제공한다")
    void getFilter_ownedFilter_returnsPhotoStatesInDisplayOrder() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID filter = createFilter(owner, FilterStatus.DRAFT);
        UUID ready = createUpload(owner, PostImageUploadStatus.READY);
        UUID issued = createUpload(owner, PostImageUploadStatus.ISSUED);
        UUID rejected = createUpload(owner, PostImageUploadStatus.REJECTED);
        linkPhoto(
                filter,
                ready,
                2
        );
        linkPhoto(
                filter,
                rejected,
                3
        );
        linkPhoto(
                filter,
                issued,
                1
        );

        // When & Then
        performAs(owner, get(DETAIL_PATH, filter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterId").value(filter.toString()))
                .andExpect(jsonPath("$.artistId").value(owner.toString()))
                .andExpect(jsonPath("$.name").value("테스트 필터"))
                .andExpect(jsonPath("$.description").value("필터 설명"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.photos", hasSize(3)))
                .andExpect(jsonPath("$.photos[0].uploadId").value(issued.toString()))
                .andExpect(jsonPath("$.photos[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.photos[0].originalImageUrl").doesNotExist())
                .andExpect(jsonPath("$.photos[0].thumbnailImageUrl").doesNotExist())
                .andExpect(jsonPath("$.photos[1].uploadId").value(ready.toString()))
                .andExpect(jsonPath("$.photos[1].status").value("READY"))
                .andExpect(jsonPath("$.photos[1].originalImageUrl")
                        .value(endsWith("/original/" + ready + ".webp")))
                .andExpect(jsonPath("$.photos[1].thumbnailImageUrl")
                        .value(endsWith("/thumbnail/" + ready + ".webp")))
                .andExpect(jsonPath("$.photos[2].uploadId").value(rejected.toString()))
                .andExpect(jsonPath("$.photos[2].status").value("REJECTED"))
                .andExpect(jsonPath("$.photos[2].originalImageUrl").doesNotExist())
                .andExpect(jsonPath("$.photos[2].thumbnailImageUrl").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(value = FilterStatus.class, names = {"DRAFT", "PRIVATE"})
    @DisplayName("다른 회원의 초안·비공개 필터는 상세에서 404를 반환한다")
    void getFilter_otherMembersHiddenFilter_returnsNotFound(FilterStatus filterStatus)
            throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID viewer = createUser(jdbcTemplate);
        UUID filter = createFilter(owner, filterStatus);

        // When & Then
        performAs(viewer, get(DETAIL_PATH, filter)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("일반 회원도 다른 회원의 공개 필터 상세와 처리 완료된 사진을 조회할 수 있다")
    void getFilter_otherMembersPublicFilter_returnsReadyPhotos() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID viewer = createUser(jdbcTemplate);
        UUID filter = createFilter(owner, FilterStatus.PUBLIC);
        UUID pending = createUpload(owner, PostImageUploadStatus.ISSUED);
        UUID ready = createUpload(owner, PostImageUploadStatus.READY);
        linkPhoto(
                filter,
                pending,
                1
        );
        linkPhoto(
                filter,
                ready,
                2
        );

        // When & Then
        performAs(viewer, get(DETAIL_PATH, filter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterId").value(filter.toString()))
                .andExpect(jsonPath("$.artistId").value(owner.toString()))
                .andExpect(jsonPath("$.status").value("PUBLIC"))
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].uploadId").value(ready.toString()))
                .andExpect(jsonPath("$.photos[0].originalImageUrl")
                        .value(endsWith("/original/" + ready + ".webp")));
    }

    @Test
    @DisplayName("탈퇴한 회원의 공개 필터는 다른 회원의 목록과 상세에 표시되지 않는다")
    void getPublicFilters_withdrawnAuthor_hidesListAndDetail() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID viewer = createUser(jdbcTemplate);
        UUID filter = createFilter(owner, FilterStatus.PUBLIC);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", owner);

        // When & Then
        performAs(viewer, get(LIST_PATH, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters", hasSize(0)));
        performAs(viewer, get(DETAIL_PATH, filter)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("삭제된 필터와 존재하지 않는 필터는 상세에서 404를 반환한다")
    void getFilter_deletedOrMissingFilter_returnsNotFound() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID filter = createFilter(owner, FilterStatus.DRAFT);
        jdbcTemplate.update("UPDATE filters SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                filter);

        // When & Then
        performAs(owner, get(DETAIL_PATH, filter)).andExpect(status().isNotFound());
        performAs(owner, get(DETAIL_PATH, UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("정지되거나 작가 권한이 회수되어도 본인 목록과 상세를 볼 수 있다")
    void getOwnedFilters_suspendedFormerArtist_returnsOwnedFilters() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET role = 'ARTIST' WHERE id = ?", owner);
        UUID filter = createFilter(owner, FilterStatus.DRAFT);
        jdbcTemplate.update("UPDATE users SET status = 'BANNED', role = 'USER' WHERE id = ?",
                owner);

        // When & Then
        performAs(owner, get(LIST_PATH, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters[0].filterId").value(filter.toString()));
        performAs(owner, get(DETAIL_PATH, filter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos", hasSize(0)));
    }

    @Test
    @DisplayName("미인증 요청과 탈퇴 회원의 기존 토큰은 두 조회 API에서 모두 거절된다")
    void getOwnedFilters_unauthenticatedOrWithdrawn_returnsUnauthorized() throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);
        UUID filter = createFilter(owner, FilterStatus.DRAFT);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", owner);

        // When & Then
        mockMvc.perform(get(LIST_PATH, owner)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(DETAIL_PATH, filter)).andExpect(status().isUnauthorized());
        performAs(owner, get(LIST_PATH, owner)).andExpect(status().isUnauthorized());
        performAs(owner, get(DETAIL_PATH, filter)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"page, 0", "pageSize, 0", "pageSize, 101"})
    @DisplayName("목록에서 잘못된 페이지 조건은 400을 반환한다")
    void getAuthorFilters_invalidPageParameter_returnsBadRequest(String parameter, String value)
            throws Exception {
        // Given
        UUID owner = createUser(jdbcTemplate);

        // When & Then
        performAs(owner, get(LIST_PATH, owner).param(parameter, value))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 경로 ID는 목록과 상세에서 400을 반환한다")
    void getOwnedFilters_invalidPathId_returnsBadRequest() throws Exception {
        // Given
        UUID viewer = createUser(jdbcTemplate);

        // When & Then
        performAs(viewer, get(LIST_PATH, "1-1-1-1-1")).andExpect(status().isBadRequest());
        performAs(viewer, get(DETAIL_PATH, "1-1-1-1-1")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("OpenAPI에서 목록과 상세의 경로, 토큰 인증과 응답 형식을 제공한다")
    void getOwnedFilters_openApi_exposesContracts() throws Exception {
        // Given
        String list = "$.paths['" + LIST_PATH + "'].get";
        String detail = "$.paths['" + DETAIL_PATH + "'].get";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(list + ".security[0].accessToken").exists())
                .andExpect(jsonPath(list + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/AuthorFilterListResponse"))
                .andExpect(jsonPath(list + ".responses['403']").exists())
                .andExpect(jsonPath(detail + ".security[0].accessToken").exists())
                .andExpect(jsonPath(detail + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/FilterDetailResponse"))
                .andExpect(jsonPath(detail + ".responses['404']").exists());
    }

    private UUID createFilter(UUID owner, FilterStatus filterStatus) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO filters (artist_id, name, description, status, published_at)
                        VALUES (?, '테스트 필터', '필터 설명', ?, CASE WHEN ? = 'DRAFT' THEN NULL ELSE CURRENT_TIMESTAMP END)
                        RETURNING id
                        """,
                UUID.class,
                owner,
                filterStatus.name(),
                filterStatus.name()
        );
    }

    private UUID createUpload(UUID owner, PostImageUploadStatus uploadStatus) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post_image_uploads (user_id, status, expires_at, claimed_at, image_metadata, rejection_reason)
                        VALUES (?, CAST(? AS post_image_upload_status), CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP,
                            CASE WHEN ? = 'READY' THEN '{}'::jsonb END, CASE WHEN ? = 'REJECTED' THEN 'CORRUPTED_IMAGE' END)
                        RETURNING id
                        """,
                UUID.class,
                owner,
                uploadStatus.name(),
                uploadStatus.name(),
                uploadStatus.name()
        );
    }

    private void linkPhoto(
            UUID filter,
            UUID upload,
            int sortOrder
    ) {
        jdbcTemplate.update(
                "INSERT INTO filter_photos (filter_id, upload_id, sort_order) VALUES (?, ?, ?)",
                filter,
                upload,
                sortOrder
        );
    }

    private ResultActions performAs(UUID viewer, MockHttpServletRequestBuilder request)
            throws Exception {
        String token = accessTokenProvider.issueForSession(viewer, UUID.randomUUID()).value();
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
