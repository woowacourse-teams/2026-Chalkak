package com.chalkak.backend.admin.api.v1.docs;

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
class AdminAiPromptVersionOpenApiTest extends IntegrationTestSupport {

    private static final String OPERATION =
            "$.paths['/api/v1/admin/ai-prompt-versions'].post";
    private static final String LIST_OPERATION =
            "$.paths['/api/v1/admin/ai-prompt-versions'].get";
    private static final String DETAIL_OPERATION =
            "$.paths['/api/v1/admin/ai-prompt-versions/{promptVersionId}'].get";
    private static final String REQUEST = "$.components.schemas.AiPromptVersionCreateRequest";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("프롬프트 버전 생성 API는 관리자 보안·응답 코드·요청 스키마 계약을 노출하고 사용자 문서에는 없다")
    void adminApiDocs_createVersion_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(OPERATION + ".responses.keys()")
                        .value(containsInAnyOrder("201", "400", "401", "403")))
                .andExpect(jsonPath(OPERATION + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(OPERATION
                        + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AiPromptVersionCreateRequest"))
                .andExpect(jsonPath(REQUEST + ".required").value(containsInAnyOrder(
                        "type", "systemPrompt", "userPromptTemplate", "model")))
                .andExpect(jsonPath(REQUEST + ".properties.systemPrompt.maxLength").value(50000))
                .andExpect(jsonPath(REQUEST + ".properties.userPromptTemplate.maxLength")
                        .value(2000))
                .andExpect(jsonPath(REQUEST + ".properties.model.maxLength").value(100))
                .andExpect(jsonPath(REQUEST + ".properties.memo.maxLength").value(500))
                .andExpect(jsonPath(REQUEST + ".properties.parameters['$ref']")
                        .value("#/components/schemas/AiPromptParametersRequest"))
                .andExpect(jsonPath("$.components.schemas.AiPromptVersionResponse.properties.keys()")
                        .value(containsInAnyOrder("promptVersionId", "type", "version",
                                "systemPrompt", "userPromptTemplate", "model", "parameters",
                                "outputSchemaVersion", "memo", "createdByAdminId", "createdAt")));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/ai-prompt-versions']").doesNotExist());
    }

    @Test
    @DisplayName("프롬프트 버전 목록 API는 관리자 보안·응답 코드·쿼리 파라미터·본문 없는 응답 계약을 노출한다")
    void adminApiDocs_listVersions_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LIST_OPERATION + ".responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "403")))
                .andExpect(jsonPath(LIST_OPERATION + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(LIST_OPERATION + ".parameters[*].name")
                        .value(containsInAnyOrder("type", "page", "pageSize")))
                .andExpect(jsonPath(LIST_OPERATION + ".parameters[?(@.name=='type')].required")
                        .value(contains(true)))
                .andExpect(jsonPath(LIST_OPERATION + ".parameters[?(@.name=='pageSize')].schema.maximum")
                        .value(contains(100)))
                .andExpect(jsonPath(LIST_OPERATION
                        + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/AiPromptVersionListResponse"))
                .andExpect(jsonPath("$.components.schemas.AiPromptVersionListResponse.properties.keys()")
                        .value(containsInAnyOrder("currentPage", "pageSize", "hasNext",
                                "promptVersions")))
                .andExpect(jsonPath("$.components.schemas.PromptVersionItem.properties.keys()")
                        .value(containsInAnyOrder("promptVersionId", "type", "version", "model",
                                "outputSchemaVersion", "memo", "createdByAdminId", "createdAt")));
    }

    @Test
    @DisplayName("프롬프트 버전 상세 API는 관리자 보안·404 포함 응답 코드·전체 응답 스키마를 노출한다")
    void adminApiDocs_getVersion_exposesContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DETAIL_OPERATION + ".responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "403", "404")))
                .andExpect(jsonPath(DETAIL_OPERATION + ".security[0].adminAccessToken").isArray())
                .andExpect(jsonPath(DETAIL_OPERATION + ".parameters[0].name")
                        .value("promptVersionId"))
                .andExpect(jsonPath(DETAIL_OPERATION + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(DETAIL_OPERATION + ".parameters[0].schema.format")
                        .value("uuid"))
                .andExpect(jsonPath(DETAIL_OPERATION
                        + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/AiPromptVersionResponse"));
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/ai-prompt-versions/{promptVersionId}']")
                        .doesNotExist());
    }
}
