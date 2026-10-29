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
class AdminAppVersionOpenApiTest extends IntegrationTestSupport {
    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("앱 버전 관리 문서는 관리자 보안·플랫폼·입력·응답·오류 코드와 nullable 설정을 노출한다")
    void adminApiDocs_appVersions_exposesContract() throws Exception {
        // Given
        String list = "$.paths['/api/v1/admin/app-versions'].get";
        String save = "$.paths['/api/v1/admin/app-versions/{platform}'].put";
        String response = "$.components.schemas.AdminAppVersionResponse.properties";
        // When & Then
        mvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(list + ".responses.keys()").value(containsInAnyOrder(
                        "200",
                        "401",
                        "403"
                )))
                .andExpect(jsonPath(list + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(list + ".parameters").doesNotExist())
                .andExpect(jsonPath(save + ".responses.keys()").value(containsInAnyOrder(
                        "200",
                        "400",
                        "401",
                        "403"
                )))
                .andExpect(jsonPath(save + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(save + ".parameters[0].name").value("platform"))
                .andExpect(jsonPath(save + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(save + ".parameters[0].required").value(true))
                .andExpect(jsonPath(save + ".parameters[0].schema.enum")
                        .value(containsInAnyOrder("ANDROID", "IOS")))
                .andExpect(jsonPath(save + ".requestBody.required").value(true))
                .andExpect(jsonPath("$.components.schemas.AdminAppVersionRequest.required")
                        .value(containsInAnyOrder(
                                "minimumSupportedVersion",
                                "storeUrl",
                                "reason"
                        )))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminAppVersionRequest.properties.revision.type")
                        .value(containsInAnyOrder("integer", "null")))
                .andExpect(jsonPath(response + ".keys()").value(containsInAnyOrder(
                        "platform",
                        "minimumSupportedVersion",
                        "storeUrl",
                        "revision",
                        "lastChangedBy",
                        "changeReason",
                        "createdAt",
                        "updatedAt"
                )))
                .andExpect(jsonPath(response + ".minimumSupportedVersion.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(response + ".storeUrl.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(response + ".revision.type")
                        .value(containsInAnyOrder("integer", "null")))
                .andExpect(jsonPath(response + ".lastChangedBy.format").value("uuid"))
                .andExpect(jsonPath(response + ".createdAt.format").value("date-time"))
                .andExpect(jsonPath(response + ".updatedAt.format").value("date-time"));
        mvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/app-versions']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/app-versions/{platform}']")
                        .doesNotExist());
    }
}
