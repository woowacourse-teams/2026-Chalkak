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

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("admin-api 문서는 조회 전용 피드백 계약을 제공한다")
    void adminApiDocs_exposesReadOnlyFeedbackContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.parameters[*].name")
                        .value(containsInAnyOrder("sort", "page", "pageSize")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].patch").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.responses['400']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks'].get.responses['403']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AdminFeedbackListItem.properties[*]")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AdminFeedbackListAuthor.properties[*]")
                        .exists());
    }

    @Test
    @DisplayName("user-api 문서는 관리자 피드백 경로를 노출하지 않는다")
    void userApiDocs_hidesAdminFeedbackPath() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/feedbacks']").doesNotExist());
    }
}
