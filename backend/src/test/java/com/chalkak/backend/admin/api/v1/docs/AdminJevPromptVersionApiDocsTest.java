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
class AdminJevPromptVersionApiDocsTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Jev 프롬프트 계약은 관리자 그룹에만 노출하고 생성 201과 용도 enum을 명시한다")
    void apiDocs_jevPrompts_exposesAdminContractOnly() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/jev-prompt-versions'].post.responses['201']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/jev-prompt-versions/{promptVersionId}/activation'].put")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.JevPromptVersionResponse.properties.role.enum")
                        .value(containsInAnyOrder("JEV_TOPIC_CLASSIFICATION",
                                "JEV_PHOTO_MODERATION")));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/jev-prompt-versions']").doesNotExist());
    }
}
