package com.chalkak.backend.admin.api.v1.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.converter.AdminPostSortConverter;
import com.chalkak.backend.admin.service.AdminPostDetail;
import com.chalkak.backend.admin.service.AdminPostListResult;
import com.chalkak.backend.admin.service.AdminPostModerationResult;
import com.chalkak.backend.admin.service.AdminPostModerationService;
import com.chalkak.backend.admin.service.AdminPostQueryService;
import com.chalkak.backend.admin.service.AdminPostSort;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminPostController.class)
@Import({
        GlobalExceptionHandler.class,
        AdminArgumentResolverWebMvcConfig.class,
        AdminPostSortConverter.class
})
class AdminPostControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f6");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c3");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e5");
    private static final LocalDate TOPIC_DATE = LocalDate.of(2026, 8, 12);
    private static final Instant CREATED_AT = Instant.parse("2026-08-12T03:30:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-12T03:40:00Z");
    private static final Instant MODERATED_AT = Instant.parse("2026-08-12T03:45:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-08-12T04:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminPostQueryService adminPostQueryService;

    @MockitoBean
    private AdminPostModerationService adminPostModerationService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("관리자는 기본 조건으로 전체 게시물 목록을 조회한다")
    void getPosts_withoutFilters_returnsDefaultPage() throws Exception {
        // Given
        AdminPostListResult result = new AdminPostListResult(
                1,
                20,
                true,
                List.of(new AdminPostListResult.PostSummary(
                        POST_ID,
                        "오늘의 순간",
                        ModerationStatus.PENDING,
                        new AdminPostListResult.AuthorSummary(
                                USER_ID,
                                "author@example.com",
                                UserStatus.ACTIVE,
                                null
                        ),
                        new AdminPostListResult.TopicSummary(
                                TOPIC_ID,
                                "오늘 가장 기억에 남은 순간",
                                TOPIC_DATE
                        ),
                        new AdminPostListResult.PhotoSummary(
                                PHOTO_ID,
                                "https://cdn.example.com/dev/posts/original.jpg",
                                "https://cdn.example.com/dev/posts/thumbnail.jpg"
                        ),
                        43L,
                        CREATED_AT,
                        null,
                        null
                ))
        );
        given(adminPostQueryService.getPosts(
                null,
                null,
                null,
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                1,
                20
        )).willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.posts[0].postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.posts[0].moderationStatus").value("PENDING"))
                .andExpect(jsonPath("$.posts[0].author.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.posts[0].topic.topicId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.posts[0].photo.photoId").value(PHOTO_ID.toString()));

        then(adminActorResolver).should().resolve();
        then(adminPostQueryService).should().getPosts(
                null,
                null,
                null,
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                1,
                20
        );
    }

    @Test
    @DisplayName("관리자 게시물 목록의 모든 필터와 정렬 및 페이지 조건을 서비스에 전달한다")
    void getPosts_withAllFilters_passesQueryParameters() throws Exception {
        // Given
        Instant createdAtFrom = Instant.parse("2026-08-01T00:00:00Z");
        Instant createdAtTo = Instant.parse("2026-08-31T23:59:59Z");
        AdminPostListResult result = new AdminPostListResult(2, 50, false, List.of());
        given(adminPostQueryService.getPosts(
                ModerationStatus.REJECTED,
                TOPIC_ID,
                TOPIC_DATE,
                USER_ID,
                createdAtFrom,
                createdAtTo,
                AdminPostSort.CREATED_AT_ASC,
                2,
                50
        )).willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/posts")
                        .queryParam("status", "REJECTED")
                        .queryParam("topicId", TOPIC_ID.toString())
                        .queryParam("topicDate", TOPIC_DATE.toString())
                        .queryParam("userId", USER_ID.toString())
                        .queryParam("createdAtFrom", createdAtFrom.toString())
                        .queryParam("createdAtTo", createdAtTo.toString())
                        .queryParam("sort", "createdAtAsc")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.posts").isEmpty());

        then(adminPostQueryService).should().getPosts(
                ModerationStatus.REJECTED,
                TOPIC_ID,
                TOPIC_DATE,
                USER_ID,
                createdAtFrom,
                createdAtTo,
                AdminPostSort.CREATED_AT_ASC,
                2,
                50
        );
    }

    @ParameterizedTest
    @CsvSource({
            "page, 0",
            "pageSize, 0",
            "pageSize, 101"
    })
    @DisplayName("관리자 게시물 페이지 조건이 범위를 벗어나면 400을 반환한다")
    void getPosts_invalidPagination_returnsBadRequest(
            String parameterName,
            String parameterValue
    ) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/posts")
                        .queryParam(parameterName, parameterValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminPostQueryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({
            "status, UNKNOWN",
            "topicId, invalid-uuid",
            "topicDate, 2026-13-40",
            "userId, invalid-uuid",
            "createdAtFrom, invalid-time",
            "createdAtTo, invalid-time",
            "sort, unknownSort"
    })
    @DisplayName("관리자 게시물 필터 형식이 잘못되면 400을 반환한다")
    void getPosts_invalidFilterFormat_returnsBadRequest(
            String parameterName,
            String parameterValue
    ) throws Exception {
        mockMvc.perform(get("/api/v1/admin/posts")
                        .queryParam(parameterName, parameterValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminPostQueryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @DisplayName("관리자 게시물 페이지 크기의 양쪽 경계값을 허용한다")
    void getPosts_pageSizeBoundary_returnsOk(int pageSize) throws Exception {
        // Given
        given(adminPostQueryService.getPosts(
                null,
                null,
                null,
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                1,
                pageSize
        )).willReturn(new AdminPostListResult(1, pageSize, false, List.of()));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/posts")
                        .queryParam("pageSize", Integer.toString(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(pageSize));
    }

    @Test
    @DisplayName("관리자는 삭제된 게시물을 포함한 게시물 상세 정보를 조회한다")
    void getPost_existingPost_returnsPostDetail() throws Exception {
        // Given
        AdminPostDetail detail = createDetail(
                ModerationStatus.REJECTED,
                MODERATED_AT,
                DELETED_AT
        );
        given(adminPostQueryService.getPost(POST_ID)).willReturn(detail);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/posts/{postId}", POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.title").value("오늘의 순간"))
                .andExpect(jsonPath("$.moderationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.author.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.author.email").value("author@example.com"))
                .andExpect(jsonPath("$.topic.topicId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.topic.title").value("오늘 가장 기억에 남은 순간"))
                .andExpect(jsonPath("$.topic.topicDate").value("2026-08-12"))
                .andExpect(jsonPath("$.photo.photoId").value(PHOTO_ID.toString()))
                .andExpect(jsonPath("$.photo.originalImageUrl")
                        .value("https://cdn.example.com/dev/posts/original.jpg"))
                .andExpect(jsonPath("$.photo.thumbnailImageUrl")
                        .value("https://cdn.example.com/dev/posts/thumbnail.jpg"))
                .andExpect(jsonPath("$.photo.metadata.width").value(4032))
                .andExpect(jsonPath("$.photo.metadata.height").value(3024))
                .andExpect(jsonPath("$.photo.metadata.byteSize").value(8_123_456L))
                .andExpect(jsonPath("$.imageUpload.uploadId").value(UPLOAD_ID.toString()))
                .andExpect(jsonPath("$.imageUpload.status").value("READY"))
                .andExpect(jsonPath("$.imageUpload.rejectionReason").value(nullValue()))
                .andExpect(jsonPath("$.author.deletedAt").value(DELETED_AT.toString()))
                .andExpect(jsonPath("$.likeCount").value(43L))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt").value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.moderatedAt").value(MODERATED_AT.toString()))
                .andExpect(jsonPath("$.deletedAt").value(DELETED_AT.toString()));

        then(adminActorResolver).should().resolve();
        then(adminPostQueryService).should().getPost(POST_ID);
    }

    @Test
    @DisplayName("처리 시각이 없는 게시물 상세 정보는 null로 직렬화한다")
    void getPost_withoutModeratedOrDeletedAt_returnsNullTimes() throws Exception {
        // Given
        AdminPostDetail detail = createDetail(ModerationStatus.VALIDATING, null, null);
        given(adminPostQueryService.getPost(POST_ID)).willReturn(detail);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/posts/{postId}", POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.moderationStatus").value("VALIDATING"))
                .andExpect(jsonPath("$.moderatedAt").value(nullValue()))
                .andExpect(jsonPath("$.deletedAt").value(nullValue()));
    }

    @Test
    @DisplayName("관리자 게시물 ID 형식이 올바르지 않으면 400을 반환한다")
    void getPost_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/posts/{postId}", "invalid-post-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));

        then(adminPostQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("관리자 게시물 상세 조회 대상이 없으면 404를 반환한다")
    void getPost_unknownPost_returnsNotFound() throws Exception {
        // Given
        given(adminPostQueryService.getPost(POST_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR,
                "게시물을 찾을 수 없습니다."
        ));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/posts/{postId}", POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물을 찾을 수 없습니다."));

        then(adminPostQueryService).should().getPost(POST_ID);
    }

    @Test
    @DisplayName("관리자는 대기 중인 게시물을 승인하고 처리 결과를 조회한다")
    void moderatePost_approvedRequest_returnsModerationResult() throws Exception {
        // Given
        AdminPostModerationResult result = new AdminPostModerationResult(
                POST_ID,
                ModerationStatus.APPROVED,
                ADMIN_ID,
                MODERATED_AT,
                null
        );
        given(adminPostModerationService.moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.APPROVED,
                null
        )).willReturn(result);

        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.moderatedBy").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.moderatedAt").value(MODERATED_AT.toString()))
                .andExpect(jsonPath("$.rejectionReason").value(nullValue()));

        then(adminActorResolver).should().resolve();
        then(adminPostModerationService).should().moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.APPROVED,
                null
        );
    }

    @Test
    @DisplayName("관리자는 대기 중인 게시물을 사유와 함께 거절하고 처리 결과를 조회한다")
    void moderatePost_rejectedRequest_returnsModerationResult() throws Exception {
        // Given
        String rejectionReason = "운영 정책 위반";
        AdminPostModerationResult result = new AdminPostModerationResult(
                POST_ID,
                ModerationStatus.REJECTED,
                ADMIN_ID,
                MODERATED_AT,
                rejectionReason
        );
        given(adminPostModerationService.moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.REJECTED,
                rejectionReason
        )).willReturn(result);

        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "REJECTED",
                                  "rejectionReason": "운영 정책 위반"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.moderationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.moderatedBy").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.moderatedAt").value(MODERATED_AT.toString()))
                .andExpect(jsonPath("$.rejectionReason").value(rejectionReason));

        then(adminActorResolver).should().resolve();
        then(adminPostModerationService).should().moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.REJECTED,
                rejectionReason
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"VALIDATING", "PENDING"})
    @DisplayName("승인과 거절이 아닌 검수 상태를 요청하면 400을 반환한다")
    void moderatePost_nonDecisionStatus_returnsBadRequest(String statusValue) throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "%s"
                                }
                                """.formatted(statusValue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminPostModerationService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"REJECTED\"}",
            "{\"status\":\"REJECTED\",\"rejectionReason\":null}",
            "{\"status\":\"REJECTED\",\"rejectionReason\":\"\"}",
            "{\"status\":\"REJECTED\",\"rejectionReason\":\"   \"}"
    })
    @DisplayName("거절 사유가 누락되거나 비어 있으면 400을 반환한다")
    void moderatePost_rejectedWithoutReason_returnsBadRequest(String requestBody) throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminPostModerationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("승인 요청에 거절 사유가 포함되면 400을 반환한다")
    void moderatePost_approvedWithReason_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED",
                                  "rejectionReason": "승인 요청에는 허용하지 않음"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminPostModerationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("거절 사유가 최대 길이인 500자이면 요청을 허용한다")
    void moderatePost_rejectionReasonAtMaximum_returnsModerationResult() throws Exception {
        // Given
        String rejectionReason = "가".repeat(500);
        AdminPostModerationResult result = new AdminPostModerationResult(
                POST_ID,
                ModerationStatus.REJECTED,
                ADMIN_ID,
                MODERATED_AT,
                rejectionReason
        );
        given(adminPostModerationService.moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.REJECTED,
                rejectionReason
        )).willReturn(result);

        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "REJECTED",
                                  "rejectionReason": "%s"
                                }
                                """.formatted(rejectionReason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectionReason").value(rejectionReason));

        then(adminPostModerationService).should().moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.REJECTED,
                rejectionReason
        );
    }

    @Test
    @DisplayName("거절 사유가 500자를 초과하면 400을 반환한다")
    void moderatePost_rejectionReasonOverMaximum_returnsBadRequest() throws Exception {
        // Given
        String rejectionReason = "가".repeat(501);

        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "REJECTED",
                                  "rejectionReason": "%s"
                                }
                                """.formatted(rejectionReason)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminPostModerationService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"status\":null}",
            "{\"status\":\"UNKNOWN\"}"
    })
    @DisplayName("검수 상태가 누락되거나 올바르지 않으면 400을 반환한다")
    void moderatePost_invalidStatus_returnsBadRequest(String requestBody) throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminPostModerationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("검수 요청 본문이 비어 있으면 400을 반환한다")
    void moderatePost_emptyBody_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminPostModerationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("검수할 게시물 ID 형식이 올바르지 않으면 400을 반환한다")
    void moderatePost_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", "invalid-post-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));

        then(adminPostModerationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대기 상태가 아닌 게시물의 검수를 요청하면 구분 가능한 400을 반환한다")
    void moderatePost_nonPendingPost_returnsStateInvalidError() throws Exception {
        // Given
        given(adminPostModerationService.moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.APPROVED,
                null
        )).willThrow(new BusinessException(
                ErrorCode.POST_MODERATION_STATE_INVALID,
                "대기 중인 게시물만 검수할 수 있습니다."
        ));

        // When & Then
        mockMvc.perform(put("/api/v1/admin/posts/{postId}/moderation", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("POST_MODERATION_STATE_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("대기 중인 게시물만 검수할 수 있습니다."));
    }

    private AdminPostDetail createDetail(
            ModerationStatus moderationStatus,
            Instant moderatedAt,
            Instant deletedAt
    ) {
        return new AdminPostDetail(
                POST_ID,
                "오늘의 순간",
                moderationStatus,
                new AdminPostDetail.AuthorDetail(
                        USER_ID,
                        "author@example.com",
                        UserStatus.ACTIVE,
                        deletedAt
                ),
                new AdminPostDetail.TopicDetail(
                        TOPIC_ID,
                        "오늘 가장 기억에 남은 순간",
                        TOPIC_DATE,
                        Instant.parse("2026-08-12T00:00:00Z"),
                        Instant.parse("2026-08-13T00:00:00Z"),
                        null
                ),
                new AdminPostDetail.PhotoDetail(
                        PHOTO_ID,
                        "https://cdn.example.com/dev/posts/original.jpg",
                        "https://cdn.example.com/dev/posts/thumbnail.jpg",
                        Map.of(
                                "width", 4032,
                                "height", 3024,
                                "byteSize", 8_123_456L
                        ),
                        CREATED_AT,
                        UPDATED_AT,
                        null
                ),
                new AdminPostDetail.ImageUploadDetail(
                        UPLOAD_ID,
                        moderationStatus == ModerationStatus.VALIDATING
                                ? PostImageUploadStatus.ISSUED
                                : PostImageUploadStatus.READY,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                ),
                43L,
                CREATED_AT,
                UPDATED_AT,
                moderatedAt,
                null,
                null,
                deletedAt
        );
    }
}
