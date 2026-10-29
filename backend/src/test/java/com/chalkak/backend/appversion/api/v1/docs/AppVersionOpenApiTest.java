package com.chalkak.backend.appversion.api.v1.docs;

import static org.hamcrest.Matchers.contains;
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
class AppVersionOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("앱 버전 문서는 공개 조회·필수 플랫폼·nullable 응답 계약을 노출한다")
    void userApiDocs_appVersion_exposesPublicNullableContract() throws Exception {
        // When & Then
        String path = "$.paths['/api/v1/app-version'].get";
        String properties = "$.components.schemas.AppVersionResponse.properties";
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".security").doesNotExist())
                .andExpect(jsonPath(path + ".responses.keys()")
                        .value(containsInAnyOrder(
                                "200",
                                "400",
                                "401",
                                "403"
                        )))
                .andExpect(jsonPath(path + ".parameters[*].name").value(contains("platform")))
                .andExpect(jsonPath(path + ".parameters[0].in").value("query"))
                .andExpect(jsonPath(path + ".parameters[0].required").value(true))
                .andExpect(jsonPath(path + ".parameters[0].schema.type").value("string"))
                .andExpect(jsonPath(path + ".parameters[0].schema.enum")
                        .value(containsInAnyOrder("ANDROID", "IOS")))
                .andExpect(jsonPath(properties + ".keys()")
                        .value(containsInAnyOrder(
                                "platform",
                                "minimumSupportedVersion",
                                "storeUrl"
                        )))
                .andExpect(jsonPath(properties + ".minimumSupportedVersion.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(properties + ".storeUrl.type")
                        .value(containsInAnyOrder("string", "null")));
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/app-version']").doesNotExist());
    }
}
