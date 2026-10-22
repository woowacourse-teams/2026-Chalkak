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
class AdminReminderOpenApiTest extends IntegrationTestSupport {

    private static final String REMINDERS = "$.paths['/api/v1/admin/reminders']";
    private static final String REMINDER = "$.paths['/api/v1/admin/reminders/{reminderId}']";
    private static final String REQUEST = "$.components.schemas.AdminReminderRequest";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("리마인더 목록 API는 관리자 보안·페이지 파라미터·응답 코드 계약을 노출한다")
    void adminApiDocs_getReminders_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(REMINDERS + ".get.responses.keys()")
                        .value(containsInAnyOrder(
                                "200",
                                "400",
                                "401",
                                "403"
                        )))
                .andExpect(jsonPath(REMINDERS + ".get.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(REMINDERS + ".get.parameters[*].name")
                        .value(containsInAnyOrder("page", "pageSize")))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminReminderListResponse.properties.reminders.items['$ref']")
                        .value("#/components/schemas/AdminReminderResponse"));
    }

    @Test
    @DisplayName("리마인더 수정 API는 관리자 보안·UUID 경로·요청 본문·응답 코드 계약을 노출한다")
    void adminApiDocs_updateReminder_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(REMINDER + ".put.responses.keys()")
                        .value(containsInAnyOrder(
                                "200",
                                "400",
                                "401",
                                "403",
                                "404"
                        )))
                .andExpect(jsonPath(REMINDER + ".put.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(REMINDER + ".put.parameters[0].schema.format").value("uuid"))
                .andExpect(jsonPath(REMINDER
                        + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AdminReminderRequest"))
                .andExpect(jsonPath(REMINDER
                        + ".put.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/AdminReminderResponse"));
    }

    @Test
    @DisplayName("리마인더 삭제 API는 관리자 보안·UUID 경로·본문 없음·응답 코드 계약을 노출한다")
    void adminApiDocs_deleteReminder_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(REMINDER + ".delete.responses.keys()")
                        .value(containsInAnyOrder(
                                "204",
                                "400",
                                "401",
                                "403",
                                "404"
                        )))
                .andExpect(jsonPath(REMINDER + ".delete.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(REMINDER + ".delete.parameters[0].schema.format").value("uuid"))
                .andExpect(jsonPath(REMINDER + ".delete.requestBody").doesNotExist());
    }

    @Test
    @DisplayName("리마인더 등록 API는 관리자 보안·필수 필드·반복 방식·대상·시각 형식·응답 코드 계약을 노출한다")
    void adminApiDocs_createReminder_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(REMINDERS + ".post.responses.keys()")
                        .value(containsInAnyOrder(
                                "201",
                                "400",
                                "401",
                                "403"
                        )))
                .andExpect(jsonPath(REMINDERS + ".post.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(REMINDERS
                        + ".post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AdminReminderRequest"))
                .andExpect(jsonPath(REQUEST + ".required")
                        .value(containsInAnyOrder(
                                "title",
                                "body",
                                "repeat",
                                "target",
                                "scheduledAt"
                        )))
                .andExpect(jsonPath(REQUEST + ".properties.repeat.enum")
                        .value(containsInAnyOrder("ONCE", "DAILY")))
                .andExpect(jsonPath(REQUEST + ".properties.target.enum")
                        .value(containsInAnyOrder(
                                "ALL",
                                "TOPIC_PARTICIPANTS",
                                "TOPIC_NON_PARTICIPANTS"
                        )))
                .andExpect(jsonPath(REQUEST + ".properties.scheduledAt.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.AdminReminderResponse.properties.keys()")
                        .value(containsInAnyOrder(
                                "reminderId",
                                "title",
                                "body",
                                "repeat",
                                "target",
                                "scheduledAt",
                                "createdAt",
                                "updatedAt"
                        )));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(REMINDERS).doesNotExist());
    }
}
