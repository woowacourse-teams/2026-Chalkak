package com.chalkak.backend.post.api.v1.docs;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
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
    @DisplayName("게시물 목록 조회는 익명 호출과 accessToken 호출을 모두 허용하는 선택적 인증으로 문서화된다")
    void userApiDocs_postListEndpoint_declaresOptionalAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security.length()").value(2))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security[0]").value(anEmptyMap()))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security[1]")
                        .value(aMapWithSize(1)))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security[1]").value(hasKey("accessToken")));
    }
}
