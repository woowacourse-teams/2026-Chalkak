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

        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}/moderation']"
                        + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AdminPostModerationRequest"))
                .andExpect(jsonPath("$.components.schemas.AdminPostModerationRequest"
                        + ".properties.status.enum")
                        .value(containsInAnyOrder("APPROVED", "REJECTED")))
                .andExpect(jsonPath("$.components.schemas.AdminPostModerationRequest"
                        + ".properties.rejectionReason.maxLength").value(500))
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}/moderation']"
                        + ".put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}/moderation']"
                        + ".put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}/moderation']"
                        + ".put.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}/moderation']"
                        + ".put.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailResponse"
                        + ".properties.moderatedBy").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailResponse"
                        + ".properties.rejectionReason").exists());

        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}']"
                        + ".delete.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AdminPostDeletionRequest"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDeletionRequest"
                        + ".required[0]").value("reason"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDeletionRequest"
                        + ".properties.reason.minLength").value(1))
                .andExpect(jsonPath("$.components.schemas.AdminPostDeletionRequest"
                        + ".properties.reason.maxLength").value(500))
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}']"
                        + ".delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}']"
                        + ".delete.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}']"
                        + ".delete.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}']"
                        + ".delete.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailResponse"
                        + ".properties.mediaDeletion.oneOf[0]['$ref']")
                        .value("#/components/schemas/AdminPostDetailMediaDeletion"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailResponse"
                        + ".properties.mediaDeletion.oneOf[1].type")
                        .value("null"))
                .andExpect(jsonPath("$.components.schemas.AdminPostDetailMediaDeletion"
                        + ".properties.status.enum")
                        .value(containsInAnyOrder("PENDING", "FAILED", "SUCCEEDED")));

        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}']").doesNotExist());
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}/moderation']")
                        .doesNotExist());
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts/{postId}'].delete")
                        .doesNotExist());
    }
}
