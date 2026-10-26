package com.chalkak.backend.admin.api.v1.docs;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminAiReviewOpenApiTest extends IntegrationTestSupport {

    private static final String LIST_OPERATION = "$.paths['/api/v1/admin/ai-reviews'].get";
    private static final String DETAIL_OPERATION = "$.paths['/api/v1/admin/ai-reviews/{aiReviewId}'].get";
    private static final String SCHEMAS = "$.components.schemas.";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("AI 리뷰 목록 API는 관리자 보안·응답 코드·쿼리 파라미터·응답 스키마 계약을 노출하고 사용자 문서에는 없다")
    void adminApiDocs_listAiReviews_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LIST_OPERATION + ".responses.keys()")
                        .value(containsInAnyOrder(
                                "200",
                                "400",
                                "401",
                                "403"
                        )))
                .andExpect(jsonPath(LIST_OPERATION + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(LIST_OPERATION + ".parameters[*].name")
                        .value(containsInAnyOrder(
                                "status",
                                "promptVersionId",
                                "page",
                                "pageSize"
                        )))
                .andExpect(jsonPath(LIST_OPERATION + ".parameters[?(@.name=='status')].required")
                        .value(contains(false)))
                .andExpect(jsonPath(
                        LIST_OPERATION + ".parameters[?(@.name=='promptVersionId')].schema.format")
                        .value(contains("uuid")))
                .andExpect(jsonPath(
                        LIST_OPERATION + ".parameters[?(@.name=='pageSize')].schema.maximum")
                        .value(contains(100)))
                .andExpect(jsonPath(
                        LIST_OPERATION + ".parameters[?(@.name=='page')].schema.minimum")
                        .value(contains(1)))
                .andExpect(jsonPath(LIST_OPERATION
                        + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/AdminAiReviewListResponse"))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewListResponse.properties.keys()")
                        .value(containsInAnyOrder(
                                "currentPage",
                                "pageSize",
                                "hasNext",
                                "aiReviews"
                        )))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewListItem.properties.keys()")
                        .value(containsInAnyOrder(
                                "aiReviewId",
                                "status",
                                "errorCode",
                                "requestCount",
                                "overallScore",
                                "promptVersion",
                                "post",
                                "author",
                                "usage",
                                "latencyMs",
                                "requestedAt",
                                "lastRequestedAt",
                                "completedAt",
                                "visibleToAuthor"
                        )))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewListItem.properties.status.enum")
                        .value(containsInAnyOrder(
                                "QUEUED",
                                "PROCESSING",
                                "COMPLETED",
                                "FAILED"
                        )))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewListPromptVersion.properties.keys()")
                        .value(containsInAnyOrder(
                                "promptVersionId",
                                "version",
                                "model"
                        )))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewListPost.properties.keys()")
                        .value(containsInAnyOrder(
                                "postId",
                                "thumbnailUrl",
                                "moderationStatus",
                                "deletedAt"
                        )))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewListAuthor.properties.keys()")
                        .value(containsInAnyOrder(
                                "userId",
                                "email",
                                "status",
                                "deletedAt"
                        )))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewListUsage.properties.keys()")
                        .value(containsInAnyOrder(
                                "inputTokens",
                                "cachedInputTokens",
                                "outputTokens",
                                "reasoningTokens"
                        )));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/ai-reviews']").doesNotExist());
    }

    @Test
    @DisplayName("AI 리뷰 상세 API는 경로 파라미터·응답 코드·응답 스키마 계약을 노출하고 사용자 문서에는 없다")
    void adminApiDocs_getAiReview_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DETAIL_OPERATION + ".responses.keys()")
                        .value(containsInAnyOrder(
                                "200",
                                "400",
                                "401",
                                "403",
                                "404"
                        )))
                .andExpect(jsonPath(DETAIL_OPERATION + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(DETAIL_OPERATION + ".parameters[*].name")
                        .value(contains("aiReviewId")))
                .andExpect(jsonPath(DETAIL_OPERATION + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(DETAIL_OPERATION + ".parameters[0].schema.format")
                        .value("uuid"))
                .andExpect(jsonPath(DETAIL_OPERATION
                        + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/AdminAiReviewDetailResponse"))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewDetailResponse.properties.keys()")
                        .value(containsInAnyOrder(
                                "aiReviewId",
                                "status",
                                "errorCode",
                                "requestCount",
                                "overallScore",
                                "promptVersion",
                                "post",
                                "author",
                                "usage",
                                "latencyMs",
                                "requestedAt",
                                "lastRequestedAt",
                                "startedAt",
                                "completedAt",
                                "visibleToAuthor",
                                "attempt",
                                "openaiResponseId",
                                "result",
                                "output"
                        )))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewDetailResponse.properties.output.type")
                        .value("object"))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewDetailPost.properties.keys()")
                        .value(containsInAnyOrder(
                                "postId",
                                "thumbnailUrl",
                                "imageUrl",
                                "moderationStatus",
                                "deletedAt"
                        )))
                .andExpect(jsonPath(SCHEMAS + "AdminAiReviewListPost.properties.keys()")
                        .value(containsInAnyOrder(
                                "postId",
                                "thumbnailUrl",
                                "moderationStatus",
                                "deletedAt"
                        )));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/ai-reviews/{aiReviewId}']")
                        .doesNotExist());
    }
}
