package com.chalkak.backend.notification.api.v1.docs;

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
class PushDeviceOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("기기 등록 문서는 토큰만 필수로 받고 인증과 실제 응답 코드를 제공한다")
    void userApiDocs_exposesCurrentDeviceContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/push-devices/current'].put.security[0]"
                        + ".accessToken").exists())
                .andExpect(jsonPath("$.paths['/api/v1/push-devices/current'].put.parameters")
                        .doesNotExist())
                .andExpect(
                        jsonPath("$.paths['/api/v1/push-devices/current'].put.requestBody.required")
                                .value(true))
                .andExpect(jsonPath("$.paths['/api/v1/push-devices/current'].put.requestBody"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PushDeviceRegistrationRequest"))
                .andExpect(
                        jsonPath("$.components.schemas.PushDeviceRegistrationRequest.required[0]")
                                .value("fcmToken"))
                .andExpect(jsonPath("$.components.schemas.PushDeviceRegistrationRequest.properties"
                        + ".fcmToken.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.PushDeviceRegistrationRequest.properties"
                        + ".sessionId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PushDeviceRegistrationRequest.properties"
                        + ".userId").doesNotExist())
                .andExpect(
                        jsonPath("$.paths['/api/v1/push-devices/current'].put.responses.length()")
                                .value(4))
                .andExpect(jsonPath("$.paths['/api/v1/push-devices/current'].put.responses['204']"
                        + ".content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/push-devices/current'].put.responses['400']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/push-devices/current'].put.responses['401']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/push-devices/current'].put.responses['403']")
                        .exists());
    }
}
