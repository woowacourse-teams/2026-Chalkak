package com.chalkak.backend.post.api.v1.docs;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
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
class PostOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("user-api 문서는 본인 게시물 삭제 계약을 제공한다")
    void userApiDocs_deletePost_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete"
                        + ".parameters[?(@.name == 'postId')].schema.format")
                        .value(containsInAnyOrder("uuid")))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete"
                        + ".security[0].accessToken").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete"
                        + ".requestBody").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete.responses['204']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete.responses['400']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete.responses['401']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete.responses['403']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].delete.responses['404']")
                        .exists());
    }

    @Test
    @DisplayName("user-api 문서는 게시물 제목 수정 계약을 제공한다")
    void userApiDocs_updatePost_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put"
                        + ".parameters[?(@.name == 'postId')].schema.format")
                        .value(containsInAnyOrder("uuid")))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put"
                        + ".security[0].accessToken").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put"
                        + ".requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put"
                        + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PostUpdateRequest"))
                .andExpect(jsonPath("$.components.schemas.PostUpdateRequest.required")
                        .value(containsInAnyOrder("title")))
                .andExpect(jsonPath("$.components.schemas.PostUpdateRequest"
                        + ".properties.title.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.PostUpdateResponse"
                        + ".properties.title.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.PostUpdateResponse"
                        + ".properties.moderationStatus").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put.responses['400']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put.responses['401']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put.responses['403']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put.responses['404']")
                        .exists());
    }

    @Test
    @DisplayName("게시물 목록 조회는 익명 호출과 accessToken 호출을 모두 허용하는 선택적 인증으로 문서화된다")
    void userApiDocs_postListEndpoint_declaresOptionalAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security.length()").value(2))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security[0]").value(anEmptyMap()))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security[1]")
                        .value(aMapWithSize(1)))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security[1]")
                        .value(hasKey("accessToken")));
    }
}
