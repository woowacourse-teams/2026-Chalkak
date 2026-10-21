package com.chalkak.backend.aireview.api.v1.docs;

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
class PostAiReviewOpenApiTest extends IntegrationTestSupport {

    private static final String PATH = "$.paths['/api/v1/posts/{postId}/ai-review']";
    private static final String SCHEMAS = "$.components.schemas";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("AI 리뷰 요청 문서는 인증·게시물 ID·202와 200 응답·오류 코드를 제공한다")
    void userApiDocs_exposesRequestContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PATH + ".post.security[0].accessToken").exists())
                .andExpect(jsonPath(PATH + ".post.parameters.length()").value(1))
                .andExpect(jsonPath(PATH + ".post.parameters[0].name").value("postId"))
                .andExpect(jsonPath(PATH + ".post.requestBody").doesNotExist())
                .andExpect(jsonPath(PATH + ".post.responses.length()").value(6))
                .andExpect(jsonPath(PATH + ".post.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/PostAiReviewResponse"))
                .andExpect(jsonPath(
                        PATH + ".post.responses['202'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PostAiReviewResponse"))
                .andExpect(jsonPath(PATH + ".post.responses['400'].content['application/json']"
                        + ".schema['$ref']").value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(PATH + ".post.responses['401']").exists())
                .andExpect(jsonPath(PATH + ".post.responses['403']").exists())
                .andExpect(jsonPath(PATH + ".post.responses['404']").exists());
    }

    @Test
    @DisplayName("AI 리뷰 조회 문서는 정지 회원 403 없이 200·401·404를 제공한다")
    void userApiDocs_exposesQueryContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PATH + ".get.security[0].accessToken").exists())
                .andExpect(jsonPath(PATH + ".get.parameters[0].name").value("postId"))
                .andExpect(jsonPath(PATH + ".get.responses.length()").value(4))
                .andExpect(jsonPath(PATH + ".get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/PostAiReviewResponse"))
                .andExpect(jsonPath(PATH + ".get.responses['400']").exists())
                .andExpect(jsonPath(PATH + ".get.responses['401']").exists())
                .andExpect(jsonPath(PATH + ".get.responses['403']").doesNotExist())
                .andExpect(jsonPath(PATH + ".get.responses['404']").exists());
    }

    @Test
    @DisplayName("응답 스키마는 상태·시각·결과를 고정된 필드 이름으로 제공한다")
    void userApiDocs_exposesResponseSchema() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResponse.properties.length()")
                        .value(4))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResponse.properties.status.enum")
                        .value(containsInAnyOrder(
                                "NONE",
                                "PROCESSING",
                                "COMPLETED",
                                "FAILED"
                        )))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResponse.properties.requestedAt")
                        .exists())
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResponse.properties.requestedAt.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResponse.properties.completedAt")
                        .exists())
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResponse.properties.result['$ref']")
                        .value("#/components/schemas/PostAiReviewResult"))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResponse.required")
                        .value(containsInAnyOrder("status")))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResult.properties.length()").value(4))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResult.properties.overallScore.type")
                        .value("number"))
                .andExpect(
                        jsonPath(SCHEMAS + ".PostAiReviewResult.properties.criteria.items['$ref']")
                                .value("#/components/schemas/PostAiReviewCriterion"))
                .andExpect(jsonPath(
                        SCHEMAS + ".PostAiReviewResult.properties.distinctivePerspective.type")
                        .value("boolean"))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewResult.properties.feedback.type")
                        .value("string"))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewCriterion.properties.length()")
                        .value(3))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewCriterion.properties.key.type")
                        .value("string"))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewCriterion.properties.label.type")
                        .value("string"))
                .andExpect(jsonPath(SCHEMAS + ".PostAiReviewCriterion.properties.score.type")
                        .value("number"));
    }
}
