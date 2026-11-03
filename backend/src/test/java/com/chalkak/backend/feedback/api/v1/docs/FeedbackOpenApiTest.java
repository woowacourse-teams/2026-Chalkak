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

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("문의 목록을 제공하며 미구현 수정·삭제 경로는 노출하지 않는다")
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
