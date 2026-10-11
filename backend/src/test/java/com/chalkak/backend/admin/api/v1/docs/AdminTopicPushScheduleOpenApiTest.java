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
    @DisplayName("조회 API는 관리자 보안·응답 코드·목록 스키마 계약을 노출한다")
    void adminApiDocs_getSchedules_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules'].get.responses.keys()")
                        .value(containsInAnyOrder("200", "401", "403")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules'].get.security[0].adminAccessToken")
                        .isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules'].get.parameters")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleListResponse.properties.schedules.type")
                        .value("array"))
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleListResponse.properties.schedules.items['$ref']")
                        .value("#/components/schemas/TopicPushScheduleResponse"));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/api/v1/admin/topic-push-schedules']").doesNotExist());
    }

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
                        "$.paths['/api/v1/admin/topic-push-schedules'].post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/TopicPushScheduleRequest"))
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleRequest.required")
                        .value(containsInAnyOrder("sendTime")))
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleRequest.properties.sendTime.format")
                        .value("time"))
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleRequest.properties.sendTime.description")
                        .value("KST 기준 발송 시각. 날짜·시간대 없이 HH:mm:ss 형식으로 전달하며 초는 00이어야 합니다."))
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleRequest.properties.enabled")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleResponse.properties.enabled")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleResponse.properties.createdAt.format")
                        .value("date-time"));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/api/v1/admin/topic-push-schedules']").doesNotExist());
    }

    @Test
    @DisplayName("수정 API는 설정 ID·필수 시각·관리자 보안·오류 계약을 노출한다")
    void adminApiDocs_updateSchedule_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].put.responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "403", "404")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].put.security[0].adminAccessToken")
                        .isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].put.parameters[0].name")
                        .value("scheduleId"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].put.parameters[0].required")
                        .value(true))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].put.parameters[0].schema.format")
                        .value("uuid"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/TopicPushScheduleRequest"))
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleRequest.required")
                        .value(containsInAnyOrder("sendTime")))
                .andExpect(jsonPath(
                        "$.components.schemas.TopicPushScheduleRequest.properties.sendTime.format")
                        .value("time"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].put.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/TopicPushScheduleResponse"));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}']")
                        .doesNotExist());
    }

    @Test
    @DisplayName("삭제 API는 설정 ID·관리자 보안·본문 없는 응답 계약을 노출한다")
    void adminApiDocs_deleteSchedule_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].delete.responses.keys()")
                        .value(containsInAnyOrder("204", "400", "401", "403", "404")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].delete.security[0].adminAccessToken")
                        .isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].delete.parameters[0].name")
                        .value("scheduleId"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].delete.parameters[0].required")
                        .value(true))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].delete.parameters[0].schema.format")
                        .value("uuid"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].delete.requestBody")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}'].delete.responses['204'].content")
                        .doesNotExist());
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/topic-push-schedules/{scheduleId}']")
                        .doesNotExist());
    }
}
