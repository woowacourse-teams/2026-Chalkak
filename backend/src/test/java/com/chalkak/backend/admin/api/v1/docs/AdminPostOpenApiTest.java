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
class AdminPostOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("admin-api 문서는 관리자 게시물 목록과 상세 계약을 서로 다른 스키마로 제공한다")
    void adminApiDocs_adminPostEndpoints_exposeDistinctListAndDetailSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "status",
                                "topicId",
                                "topicDate",
                                "userId",
                                "createdAtFrom",
                                "createdAtTo",
                                "sort",
                                "page",
                                "pageSize"
                        )))
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}'].get"
                        + ".parameters[?(@.name == 'postId')].schema.format")
                        .value(containsInAnyOrder("uuid")))
                .andExpect(jsonPath("$.components.schemas.AdminPostListItem"
                        + ".properties.topic['$ref']")
                        .value("#/components/schemas/AdminPostListTopic"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailResponse"
                        + ".properties.topic['$ref']")
                        .value("#/components/schemas/AdminPostDetailTopic"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailTopic"
                        + ".properties.startsAt").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailTopic"
                        + ".properties.endsAt").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailPhoto"
                        + ".properties.metadata['$ref']")
                        .value("#/components/schemas/AdminPostDetailPhotoMetadata"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailPhoto"
                        + ".properties.createdAt").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailPhotoMetadata"
                        + ".properties.width").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailPhotoMetadata"
                        + ".properties.height").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailPhotoMetadata"
                        + ".properties.byteSize").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailResponse"
                        + ".properties.imageUpload.oneOf[0]['$ref']")
                        .value("#/components/schemas/AdminPostDetailImageUpload"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailResponse"
                        + ".properties.imageUpload.oneOf[1].type")
                        .value("null"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailImageUpload"
                        + ".properties.claimedAt").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailImageUpload"
                        + ".properties.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailImageUpload"
                        + ".properties.imageMetadata").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}']").doesNotExist());
    }
}
