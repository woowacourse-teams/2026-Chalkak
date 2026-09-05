package com.chalkak.backend.admin.api.v1.docs;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminAuditLogOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminApiDocs_exposesReadOnlyAuditLogContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-logs'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "adminId", "action", "targetType", "targetId",
                                "occurredFrom", "occurredTo", "sort", "page", "pageSize"
                        )))
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-logs'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-logs'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-logs'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-logs'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-logs'].get.responses['403']").exists())
                .andExpect(jsonPath("$.components.schemas.AuditLogResponse.properties[*]")
                        .exists());

        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-logs']").doesNotExist());
    }
}
