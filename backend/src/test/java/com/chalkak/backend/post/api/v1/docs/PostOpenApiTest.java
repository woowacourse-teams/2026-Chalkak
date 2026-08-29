package com.chalkak.backend.post.api.v1.docs;

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
                        + ".security[0].userIdHeader").exists())
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
}
