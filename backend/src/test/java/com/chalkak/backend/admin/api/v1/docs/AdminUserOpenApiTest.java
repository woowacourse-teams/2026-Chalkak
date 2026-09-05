package com.chalkak.backend.admin.api.v1.docs;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
class AdminUserOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("admin-api 문서에 관리자 사용자 목록·상세 계약만 노출한다")
    void adminApiDocs_adminUserEndpoints_exposeQueryAndDetailContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "status",
                                "email",
                                "sort",
                                "page",
                                "pageSize")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/users'].get"
                        + ".parameters[?(@.name == 'status')].schema.enum")
                        .value(hasItem(containsInAnyOrder(
                                "ACTIVE",
                                "BANNED",
                                "WITHDRAWN"))))
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/{userId}'].get"
                        + ".parameters[?(@.name == 'userId')].schema.format")
                        .value(containsInAnyOrder("uuid")))
                .andExpect(jsonPath("$.components.schemas.AdminUserListItem"
                        + ".properties.postCounts['$ref']")
                        .value("#/components/schemas/AdminUserPostCounts"))
                .andExpect(jsonPath("$.components.schemas.AdminUserPostCounts"
                        + ".properties.pending").exists())
                .andExpect(jsonPath("$.components.schemas.AdminUserPostCounts"
                        + ".properties.approved").exists())
                .andExpect(jsonPath("$.components.schemas.AdminUserPostCounts"
                        + ".properties.rejected").exists())
                .andExpect(jsonPath("$.components.schemas.AdminUserPostCounts"
                        + ".properties.total").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AdminUserPostCounts"
                        + ".properties.validating").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AdminUserDetailResponse"
                        + ".properties.signature['$ref']")
                        .value("#/components/schemas/AdminUserSignature"))
                .andExpect(jsonPath("$.components.schemas.AdminUserSignature"
                        + ".properties.originalImageUrl.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/{userId}'].get"
                        + ".responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/{userId}/status'].patch"
                        + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AdminUserStatusUpdateRequest"))
                .andExpect(jsonPath("$.components.schemas.AdminUserStatusUpdateRequest"
                        + ".properties.status.enum")
                        .value(containsInAnyOrder("ACTIVE", "BANNED")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/{userId}/status'].patch"
                        + ".responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/{userId}/status'].patch"
                        + ".responses['404']").exists());

        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/{userId}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/{userId}/status']")
                        .doesNotExist());
    }
}
