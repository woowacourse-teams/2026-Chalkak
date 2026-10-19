package com.chalkak.backend.admin.banner.api.v1.docs;

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
class AdminBannerOpenApiTest extends IntegrationTestSupport {

    private static final String PATH = "$.paths['/api/v1/admin/banners']";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("배너 등록 문서는 관리자 보안·필수 입력·응답 필드·상태 코드를 노출한다")
    void adminApiDocs_createBanner_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PATH + ".post.responses.keys()")
                        .value(containsInAnyOrder("201", "400", "401", "403")))
                .andExpect(jsonPath(PATH + ".post.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(PATH + ".post.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AdminBannerRequest.required")
                        .value(containsInAnyOrder("title", "subtitle", "webViewUrl",
                                "thumbnailUrl")))
                .andExpect(jsonPath("$.components.schemas.AdminBannerResponse.properties.keys()")
                        .value(containsInAnyOrder("id", "title", "subtitle", "webViewUrl",
                                "thumbnailUrl", "createdAt")))
                .andExpect(jsonPath("$.components.schemas.AdminBannerResponse.properties.id.format")
                        .value("uuid"))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminBannerResponse.properties.createdAt.format")
                        .value("date-time"));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PATH).doesNotExist());
    }
}
