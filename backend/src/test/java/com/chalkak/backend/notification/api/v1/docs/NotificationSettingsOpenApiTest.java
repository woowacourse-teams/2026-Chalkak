package com.chalkak.backend.notification.api.v1.docs;

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
class NotificationSettingsOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("수신 설정 조회 문서는 인증·두 불리언 필드·실제 응답 코드를 제공한다")
    void userApiDocs_exposesSettingsQueryContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/notification-settings'].get.security[0].accessToken")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/notification-settings'].get.parameters")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/notification-settings'].get.requestBody")
                        .doesNotExist())
                .andExpect(
                        jsonPath("$.paths['/api/v1/notification-settings'].get.responses.length()")
                                .value(3))
                .andExpect(jsonPath("$.paths['/api/v1/notification-settings'].get.responses['200']"
                        + ".content['*/*'].schema['$ref']")
                        .value("#/components/schemas/NotificationSettingsResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/notification-settings'].get.responses['401']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/notification-settings'].get.responses['403']")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSettingsResponse.properties.length()")
                        .value(2))
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSettingsResponse.properties.topicPushEnabled.type")
                        .value("boolean"))
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSettingsResponse.properties.moderationPushEnabled.type")
                        .value("boolean"));
    }

    @Test
    @DisplayName("수신 설정 수정 문서는 선택 필드·인증·빈 성공 응답·오류 코드를 제공한다")
    void userApiDocs_exposesSettingsUpdateContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/notification-settings'].patch.security[0].accessToken")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/notification-settings'].patch.parameters")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/notification-settings'].patch.requestBody.required")
                        .value(true))
                .andExpect(jsonPath("$.paths['/api/v1/notification-settings'].patch.requestBody"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/NotificationSettingsUpdateRequest"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/notification-settings'].patch.responses.length()")
                        .value(4))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/notification-settings'].patch.responses['204'].content")
                        .doesNotExist())
                .andExpect(
                        jsonPath("$.paths['/api/v1/notification-settings'].patch.responses['400']"
                                + ".content['application/json'].schema['$ref']")
                                .value("#/components/schemas/ErrorResponse"))
                .andExpect(
                        jsonPath("$.paths['/api/v1/notification-settings'].patch.responses['401']")
                                .exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/notification-settings'].patch.responses['403']")
                                .exists())
                .andExpect(
                        jsonPath("$.components.schemas.NotificationSettingsUpdateRequest.required")
                                .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSettingsUpdateRequest.properties.length()")
                        .value(2))
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSettingsUpdateRequest.properties.topicPushEnabled.type")
                        .value(containsInAnyOrder("boolean", "null")))
                .andExpect(jsonPath(
                        "$.components.schemas.NotificationSettingsUpdateRequest.properties.moderationPushEnabled.type")
                        .value(containsInAnyOrder("boolean", "null")));
    }
}
