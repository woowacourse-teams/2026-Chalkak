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
    @Test
    @DisplayName("배너 목록 문서는 페이지 쿼리·보안·응답 계약을 노출한다")
    void adminApiDocs_getBanners_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PATH + ".get.responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "403")))
                .andExpect(jsonPath(PATH + ".get.security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(PATH + ".get.parameters[*].name")
                        .value(containsInAnyOrder("page", "pageSize")))
                .andExpect(jsonPath(PATH + ".get.parameters[*].in")
                        .value(containsInAnyOrder("query", "query")))
                .andExpect(jsonPath(PATH + ".get.parameters[?(@.name == 'page')].schema.default")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(
                        jsonPath(PATH + ".get.parameters[?(@.name == 'pageSize')].schema.default")
                                .value(org.hamcrest.Matchers.contains(20)))
                .andExpect(
                        jsonPath(PATH + ".get.parameters[?(@.name == 'pageSize')].schema.maximum")
                                .value(org.hamcrest.Matchers.contains(100)))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminBannerListResponse.properties.keys()")
                        .value(containsInAnyOrder("currentPage", "pageSize", "hasNext", "banners")))
                .andExpect(jsonPath(
                        "$.components.schemas.AdminBannerListResponse.properties.banners.items['$ref']")
                        .value("#/components/schemas/AdminBannerResponse"));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PATH).doesNotExist());
    }
    @Test
    @DisplayName("배너 삭제 문서는 UUID 경로·필수 사유·관리자 보안·응답 코드를 노출한다")
    void adminApiDocs_deleteBanner_exposesContract() throws Exception {
        // When & Then
        String path = "$.paths['/api/v1/admin/banners/{bannerId}'].delete";
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".responses.keys()")
                        .value(containsInAnyOrder("204", "400", "401", "403", "404")))
                .andExpect(jsonPath(path + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(path + ".parameters[*].name")
                        .value(org.hamcrest.Matchers.contains("bannerId")))
                .andExpect(jsonPath(path + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(path + ".parameters[0].schema.format").value("uuid"))
                .andExpect(jsonPath(path + ".requestBody.required").value(true))
                .andExpect(jsonPath("$.components.schemas.AdminBannerDeletionRequest.required")
                        .value(org.hamcrest.Matchers.contains("reason")));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/banners/{bannerId}']").doesNotExist());
    }

}
