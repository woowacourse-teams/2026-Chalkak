package com.chalkak.backend.admin.api.v1.docs;

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
class AdminFeedbackOpenApiTest extends IntegrationTestSupport {

    private static final String DETAIL_PATH = "$.paths['/api/v1/admin/feedbacks/{feedbackId}'].get";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("관리자 답변 삭제 문서는 필수 answeredAt과 본문 없는 204를 제공한다")
    void adminApiDocs_exposesFeedbackAnswerDeletionContract() throws Exception {
        String path = "$.paths['/api/v1/admin/feedbacks/{feedbackId}/answer'].delete";

        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".parameters[?(@.name == 'answeredAt')].required")
                        .value(containsInAnyOrder(true)))
                .andExpect(jsonPath(path + ".parameters[?(@.name == 'answeredAt')].schema.format")
                        .value(containsInAnyOrder("date-time")))
                .andExpect(jsonPath(path + ".requestBody").doesNotExist())
                .andExpect(jsonPath(path + ".responses['204'].content").doesNotExist());
    }

    @Test
    @DisplayName("관리자 답변 문서는 요청 본문과 본문 없는 204를 제공한다")
    void adminApiDocs_exposesFeedbackAnswerContract() throws Exception {
        String path = "$.paths['/api/v1/admin/feedbacks/{feedbackId}/answer'].post";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".requestBody.required").value(true))
                .andExpect(jsonPath(path + ".responses['204'].content").doesNotExist())
                .andExpect(jsonPath(path + ".responses['400']").exists())
                .andExpect(
                        jsonPath(path + ".responses['401'].content['application/json'].schema.$ref")
                                .value("#/components/schemas/ErrorResponse"))
                .andExpect(
                        jsonPath(path + ".responses['403'].content['application/json'].schema.$ref")
                                .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(path + ".responses['404']").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackAnswerRequest.properties.answerContent.type")
                        .value("string"))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackAnswerRequest.properties.answerContent.maxLength")
                        .value(3000));
    }

    @Test
    @DisplayName("admin-api 문서는 기존 피드백 목록 계약을 제공한다")
    void adminApiDocs_exposesFeedbackListContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "userId",
                                "sort",
                                "page",
                                "pageSize"
                        )))
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].patch").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.responses['400']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.responses['401']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.responses['403']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AdminFeedbackListItem.properties[*]")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.parameters"
                        + "[?(@.name == 'userId')].required").value(containsInAnyOrder(false)))
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.parameters"
                        + "[?(@.name == 'userId')].schema.format")
                        .value(containsInAnyOrder("uuid")))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackListItem.properties.deletedAt.format")
                        .value("date-time"))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackListItem.properties.deletedAt.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.AdminFeedbackListAuthor.properties[*]")
                        .exists());
    }

    @Test
    @DisplayName("관리자 문의 상세 문서는 UUID·관리자 인증·상세 스키마와 오류 계약을 제공한다")
    void adminApiDocs_detail_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DETAIL_PATH + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(DETAIL_PATH + ".parameters[0].name").value("feedbackId"))
                .andExpect(jsonPath(DETAIL_PATH + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(DETAIL_PATH + ".parameters[0].required").value(true))
                .andExpect(jsonPath(DETAIL_PATH + ".parameters[0].schema.format").value("uuid"))
                .andExpect(jsonPath(DETAIL_PATH + ".responses['200']").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".responses['400']").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".responses['401']").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".responses['403']").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".responses['404']").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackDetailResponse.properties.feedbackId.format")
                        .value("uuid"))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackDetailResponse.properties.content.type")
                        .value("string"))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackDetailResponse.properties.createdAt.format")
                        .value("date-time"))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackDetailResponse.properties.answerContent")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackDetailResponse.properties.answeredAt.format")
                        .value("date-time"))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackDetailResponse.properties.deletedAt.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminFeedbackDetailResponse.properties.author.$ref")
                        .value("#/components/schemas/AdminFeedbackListAuthor"));
    }

    @Test
    @DisplayName("관리자 문의 삭제 문서는 본문 없는 204와 오류·권한 계약을 제공한다")
    void adminApiDocs_deletion_exposesContract() throws Exception {
        // Given
        String path = "$.paths['/api/v1/admin/feedbacks/{feedbackId}'].delete";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(path + ".parameters[0].schema.format").value("uuid"))
                .andExpect(jsonPath(path + ".parameters[0].required").value(true))
                .andExpect(jsonPath(path + ".requestBody").doesNotExist())
                .andExpect(jsonPath(path + ".responses['204']").exists())
                .andExpect(jsonPath(path + ".responses['204'].content").doesNotExist())
                .andExpect(jsonPath(path + ".responses['400']").exists())
                .andExpect(jsonPath(path + ".responses['401']").exists())
                .andExpect(jsonPath(path + ".responses['403']").exists())
                .andExpect(jsonPath(path + ".responses['404']").exists());
    }

    @Test
    @DisplayName("user-api 문서는 관리자 피드백 경로를 노출하지 않는다")
    void userApiDocs_hidesAdminFeedbackPath() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks']").doesNotExist())
                .andExpect(
                        jsonPath("$.paths['/api/v1/admin/feedbacks/{feedbackId}']").doesNotExist());
    }
}
