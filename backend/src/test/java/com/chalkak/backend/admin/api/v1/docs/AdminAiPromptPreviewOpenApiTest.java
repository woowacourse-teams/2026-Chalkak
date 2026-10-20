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
class AdminAiPromptPreviewOpenApiTest extends IntegrationTestSupport {

    private static final String OPERATION =
            "$.paths['/api/v1/admin/ai-prompt-previews'].post";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("미리보기 API는 관리자 보안·응답 코드·요청 스키마 계약을 노출하고 사용자 문서에는 없다")
    void adminApiDocs_preview_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(OPERATION + ".responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "403", "404")))
                .andExpect(jsonPath(OPERATION + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(OPERATION
                        + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AiPromptPreviewRequest"))
                .andExpect(jsonPath("$.components.schemas.AiPromptPreviewRequest.required")
                        .value(containsInAnyOrder(
                                "type", "systemPrompt", "userPromptTemplate", "model", "postId")))
                .andExpect(jsonPath(
                        "$.components.schemas.AiPromptPreviewRequest.properties.systemPrompt.maxLength")
                        .value(50000))
                .andExpect(jsonPath(
                        "$.components.schemas.AiPromptPreviewRequest.properties.userPromptTemplate.maxLength")
                        .value(2000))
                .andExpect(jsonPath(
                        "$.components.schemas.AiPromptPreviewRequest.properties.parameters['$ref']")
                        .value("#/components/schemas/AiPromptParametersRequest"))
                .andExpect(jsonPath("$.components.schemas.AiPromptPreviewResponse.properties.failureType.enum")
                        .value(containsInAnyOrder("TIMEOUT", "RATE_LIMITED", "QUOTA_EXCEEDED",
                                "UPSTREAM_ERROR", "BAD_REQUEST", "REFUSED", "INCOMPLETE", "INVALID_OUTPUT")))
                .andExpect(jsonPath("$.components.schemas.AiPromptPreviewResponse.properties.keys()")
                        .value(containsInAnyOrder("succeeded", "failureType", "retryable",
                                "result", "output", "usage", "latencyMs", "responseId")));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/ai-prompt-previews']").doesNotExist());
    }
}
