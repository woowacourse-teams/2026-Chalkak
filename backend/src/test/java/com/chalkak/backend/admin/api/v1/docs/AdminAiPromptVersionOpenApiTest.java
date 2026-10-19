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
class AdminAiPromptVersionOpenApiTest extends IntegrationTestSupport {

    private static final String OPERATION =
            "$.paths['/api/v1/admin/ai-prompt-versions'].post";
    private static final String REQUEST = "$.components.schemas.AiPromptVersionCreateRequest";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("프롬프트 버전 생성 API는 관리자 보안·응답 코드·요청 스키마 계약을 노출하고 사용자 문서에는 없다")
    void adminApiDocs_createVersion_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(OPERATION + ".responses.keys()")
                        .value(containsInAnyOrder("201", "400", "401", "403")))
                .andExpect(jsonPath(OPERATION + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(OPERATION
                        + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AiPromptVersionCreateRequest"))
                .andExpect(jsonPath(REQUEST + ".required").value(containsInAnyOrder(
                        "type", "systemPrompt", "userPromptTemplate", "model")))
                .andExpect(jsonPath(REQUEST + ".properties.systemPrompt.maxLength").value(50000))
                .andExpect(jsonPath(REQUEST + ".properties.userPromptTemplate.maxLength")
                        .value(2000))
                .andExpect(jsonPath(REQUEST + ".properties.model.maxLength").value(100))
                .andExpect(jsonPath(REQUEST + ".properties.memo.maxLength").value(500))
                .andExpect(jsonPath("$.components.schemas.AiPromptVersionResponse.properties.keys()")
                        .value(containsInAnyOrder("promptVersionId", "type", "version",
                                "systemPrompt", "userPromptTemplate", "model", "parameters",
                                "outputSchemaVersion", "memo", "createdByAdminId", "createdAt")));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/ai-prompt-versions']").doesNotExist());
    }
}
