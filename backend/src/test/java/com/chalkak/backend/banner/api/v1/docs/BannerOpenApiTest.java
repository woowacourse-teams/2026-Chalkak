package com.chalkak.backend.banner.api.v1.docs;

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
class BannerOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("앱 배너 문서는 공개 접근·페이지 기본값·응답 계약을 노출한다")
    void userApiDocs_getBanners_exposesPublicContract() throws Exception {
        // When & Then
        String path = "$.paths['/api/v1/banners'].get";
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".security").doesNotExist())
                .andExpect(jsonPath(path + ".responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "403")))
                .andExpect(jsonPath(path + ".parameters[*].name")
                        .value(containsInAnyOrder("page", "pageSize")))
                .andExpect(jsonPath(path + ".parameters[?(@.name == 'page')].schema.default")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath(path + ".parameters[?(@.name == 'pageSize')].schema.default")
                        .value(org.hamcrest.Matchers.contains(20)))
                .andExpect(jsonPath(path + ".parameters[?(@.name == 'pageSize')].schema.maximum")
                        .value(org.hamcrest.Matchers.contains(100)))
                .andExpect(jsonPath("$.components.schemas.BannerListResponse.properties.keys()")
                        .value(containsInAnyOrder("currentPage", "pageSize", "hasNext", "banners")))
                .andExpect(jsonPath("$.components.schemas.BannerResponse.properties.keys()")
                        .value(containsInAnyOrder("id", "title", "subtitle", "webViewUrl",
                                "thumbnailUrl", "createdAt")))
                .andExpect(jsonPath("$.components.schemas.BannerResponse.properties.id.format")
                        .value("uuid"))
                .andExpect(
                        jsonPath("$.components.schemas.BannerResponse.properties.createdAt.format")
                                .value("date-time"));
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/banners']").doesNotExist());
    }
}
