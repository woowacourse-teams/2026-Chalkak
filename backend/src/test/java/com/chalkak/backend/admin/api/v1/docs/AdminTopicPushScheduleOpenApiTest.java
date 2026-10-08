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
class AdminTopicPushScheduleOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("생성 API는 관리자 보안·시각 형식·필수 필드·응답 코드 계약을 노출한다")
    void adminApiDocs_createSchedule_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/api/v1/admin/topic-push-schedules'].post.responses.*")
                                .isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules'].post.responses.keys()")
                        .value(containsInAnyOrder("201", "400", "401", "403")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules'].post.security[0].adminAccessToken")
                        .isArray())
                .andExpect(jsonPath(
                        "$.components.schemas.AdminTopicPushScheduleCreationRequest.required")
                        .value(containsInAnyOrder("sendTime")))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminTopicPushScheduleCreationRequest.properties.sendTime.format")
                        .value("time"))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminTopicPushScheduleCreationRequest.properties.enabled")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.AdminTopicPushScheduleResponse.properties.enabled")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.AdminTopicPushScheduleResponse.properties.createdAt.format")
                        .value("date-time"));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/api/v1/admin/topic-push-schedules']").doesNotExist());
    }
}
