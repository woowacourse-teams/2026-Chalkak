package com.chalkak.backend.notification.api.v1.docs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
class NotificationOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("사용자 API 문서에 알림함 조회와 읽음 계약을 제공한다")
    void userApiDocs_exposesNotificationInboxContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.description")
                        .value(containsString("30일")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/notifications/{notificationId}'].get.description")
                        .value(containsString("30일")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/notifications/{notificationId}/read'].patch.description")
                        .value(containsString("30일")))
                .andExpect(jsonPath("$.paths['/api/v1/notifications/read-all'].patch.description")
                        .value(containsString("30일")))
                .andExpect(
                        jsonPath("$.paths['/api/v1/notifications/unread-status'].get.description")
                                .value(containsString("30일")))
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSummaryResponse.properties.sourceType.enum")
                        .value(hasItem("POST")))
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSummaryResponse.properties.sourceId.format")
                        .value("uuid"))
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSummaryResponse.properties.sourceType.type")
                        .value(hasItem("null")))
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSummaryResponse.properties.sourceId.type")
                        .value(hasItem("null")))
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/{notificationId}'].get"
                        + ".responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/{notificationId}/read']"
                        + ".patch.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/read-all']"
                        + ".patch.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/unread-status']"
                        + ".get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.security[0]"
                        + ".accessToken").exists());
    }
}
