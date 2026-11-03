package com.chalkak.backend.feedback.api.v1.docs;

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
class FeedbackOpenApiTest extends IntegrationTestSupport {

    private static final String SUBMIT_PATH = "$.paths['/api/v1/feedbacks'].post";
    private static final String DETAIL_PATH = "$.paths['/api/v1/feedbacks/{feedbackId}'].get";
    private static final String DELETE_PATH = "$.paths['/api/v1/feedbacks/{feedbackId}'].delete";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("문의 삭제 문서는 본인 인증·ID·204와 오류 응답을 제공한다")
    void userApiDocs_feedbackDeletion_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DELETE_PATH + ".security[0].accessToken").exists())
                .andExpect(jsonPath(DELETE_PATH + ".parameters[0].name").value("feedbackId"))
                .andExpect(jsonPath(DELETE_PATH + ".parameters[0].required").value(true))
                .andExpect(jsonPath(DELETE_PATH + ".parameters[0].schema.format").value("uuid"))
                .andExpect(jsonPath(DELETE_PATH + ".responses['204']").exists())
                .andExpect(jsonPath(DELETE_PATH + ".responses['204'].content").doesNotExist())
                .andExpect(jsonPath(DELETE_PATH + ".responses['400']").exists())
                .andExpect(jsonPath(DELETE_PATH + ".responses['401']").exists())
                .andExpect(jsonPath(DELETE_PATH + ".responses['404']").exists());
    }

    @Test
    @DisplayName("문의 상세 문서는 본인 인증과 ID·성공·오류 응답 계약을 제공한다")
    void userApiDocs_feedbackDetail_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DETAIL_PATH + ".security[0].accessToken").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".parameters[0].name").value("feedbackId"))
                .andExpect(jsonPath(DETAIL_PATH + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(DETAIL_PATH + ".parameters[0].required").value(true))
                .andExpect(jsonPath(DETAIL_PATH + ".parameters[0].schema.format").value("uuid"))
                .andExpect(jsonPath(DETAIL_PATH + ".responses['200']").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".responses['400']").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".responses['401']").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".responses['404']").exists())
                .andExpect(jsonPath(DETAIL_PATH + ".responses['403']").doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.FeedbackDetailResponse.properties.feedbackId")
                        .exists())
                .andExpect(
                        jsonPath("$.components.schemas.FeedbackDetailResponse.properties.content")
                                .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.FeedbackDetailResponse.properties.createdAt.format")
                        .value("date-time"));
    }

    @Test
    @DisplayName("user-api 문서는 피드백 제출 계약을 제공한다")
    void userApiDocs_submitFeedback_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SUBMIT_PATH).exists())
                .andExpect(jsonPath(SUBMIT_PATH + ".security[0].accessToken").exists())
                .andExpect(jsonPath(SUBMIT_PATH + ".requestBody.content"
                        + "['application/json'].schema.$ref").exists())
                .andExpect(jsonPath(SUBMIT_PATH + ".responses['201']").exists())
                .andExpect(jsonPath(SUBMIT_PATH + ".responses['400']").exists())
                .andExpect(jsonPath(SUBMIT_PATH + ".responses['400'].description")
                        .value("내용이 비었거나 1000자를 초과함, 또는 접수 간격·횟수 제한 초과"))
                .andExpect(jsonPath(SUBMIT_PATH + ".responses['401']").exists())
                .andExpect(jsonPath(SUBMIT_PATH + ".responses['403']").doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.FeedbackSubmissionRequest.properties.content.maxLength")
                        .value(1000));
    }

    @Test
    @DisplayName("문의 목록은 페이지 조건을 제공하며 수정·일괄 삭제 경로는 노출하지 않는다")
    void userApiDocs_feedback_exposesOwnListWithoutMutation() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks'].get.security[0].accessToken")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks'].get.responses['401']").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/feedbacks'].get.parameters[?(@.name == 'page')]")
                                .isNotEmpty())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/feedbacks'].get.parameters[?(@.name == 'pageSize')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/me']").doesNotExist());
    }
}
