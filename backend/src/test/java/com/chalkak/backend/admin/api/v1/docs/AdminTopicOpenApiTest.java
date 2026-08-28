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
class AdminTopicOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("admin-api 문서에 주제 CRUD와 필터 계약을 노출하고 user-api에서는 제외한다")
    void adminApiDocs_adminTopicEndpoints_exposeLifecycleContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/topics'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "phase",
                                "dateFrom",
                                "dateTo",
                                "sort",
                                "page",
                                "pageSize")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/topics'].post.responses['201']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/topics/{topicId}'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/topics/{topicId}'].put")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/topics/{topicId}'].delete")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AdminTopicMutationRequest.required")
                        .value(containsInAnyOrder(
                                "title",
                                "topicDate",
                                "startsAt",
                                "endsAt")))
                .andExpect(jsonPath("$.components.schemas.AdminTopicDetailResponse"
                        + ".properties.phase.enum")
                        .value(containsInAnyOrder("BEFORE_OPEN", "OPEN", "CLOSED")));

        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/topics']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/topics/{topicId}']")
                        .doesNotExist());
    }
}
