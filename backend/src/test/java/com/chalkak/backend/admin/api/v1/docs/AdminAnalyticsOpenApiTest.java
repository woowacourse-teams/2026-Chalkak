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
class AdminAnalyticsOpenApiTest extends IntegrationTestSupport {

    private static final String OPERATION =
            "$.paths['/api/v1/admin/analytics/aggregations'].post";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("admin-api 문서에 수동 집계 계약을 노출하고 user-api에서는 제외한다")
    void adminApiDocs_aggregationEndpoint_exposesContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(OPERATION + ".security[0].adminAccessToken").exists())
                .andExpect(jsonPath(OPERATION + ".requestBody.content['application/json']"
                        + ".schema['$ref']").value("#/components/schemas/AdminUserMetricsAggregationRequest"))
                .andExpect(jsonPath("$.components.schemas.AdminUserMetricsAggregationRequest"
                        + ".required").value(containsInAnyOrder("metricDate")))
                .andExpect(jsonPath("$.components.schemas.AdminUserMetricsAggregationRequest"
                        + ".properties.metricDate.format").value("date"))
                .andExpect(jsonPath(OPERATION + ".responses['202']").exists())
                .andExpect(jsonPath(OPERATION + ".responses['202'].content").doesNotExist())
                .andExpect(jsonPath(OPERATION + ".responses['400']").exists())
                .andExpect(jsonPath(OPERATION + ".responses['403']").exists());

        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/analytics/aggregations']")
                        .doesNotExist());
    }
}
