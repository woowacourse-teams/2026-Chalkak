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
class AdminNoticeOpenApiTest extends IntegrationTestSupport {

    private static final String NOTICES = "$.paths['/api/v1/admin/notices']";
    private static final String NOTICE = "$.paths['/api/v1/admin/notices/{noticeId}']";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("공지 목록·상세 API는 관리자 보안·페이지 파라미터·응답 코드 계약을 노출한다")
    void adminApiDocs_getNotices_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(NOTICES + ".get.responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "403")))
                .andExpect(jsonPath(NOTICES + ".get.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(NOTICES + ".get.parameters[*].name")
                        .value(containsInAnyOrder("page", "pageSize")))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminNoticeListResponse.properties.notices.items['$ref']")
                        .value("#/components/schemas/AdminNoticeResponse"))
                .andExpect(jsonPath(NOTICE + ".get.responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "403", "404")))
                .andExpect(jsonPath(NOTICE + ".get.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(NOTICE + ".get.parameters[0].schema.format").value("uuid"));
    }

    @Test
    @DisplayName("공지 등록 API는 관리자 보안·필수 필드·시각 형식·응답 코드 계약을 노출한다")
    void adminApiDocs_createNotice_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(NOTICES + ".post.responses.keys()")
                        .value(containsInAnyOrder("201", "400", "401", "403")))
                .andExpect(jsonPath(NOTICES + ".post.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(NOTICES
                        + ".post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AdminNoticeRequest"))
                .andExpect(jsonPath("$.components.schemas.AdminNoticeRequest.required")
                        .value(containsInAnyOrder("title", "body", "scheduledAt")))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminNoticeRequest.properties.scheduledAt.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.AdminNoticeResponse.properties.keys()")
                        .value(containsInAnyOrder("noticeId", "title", "body", "scheduledAt",
                                "createdAt", "updatedAt")));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(NOTICES).doesNotExist());
    }
}
