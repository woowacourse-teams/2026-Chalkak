package com.chalkak.backend.admin.api.support;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class CurrentAdminArgumentResolverTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f6");

    private AdminActorResolver adminActorResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminActorResolver = mock(AdminActorResolver.class);
        CurrentAdminArgumentResolver argumentResolver =
                new CurrentAdminArgumentResolver(adminActorResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setCustomArgumentResolvers(argumentResolver)
                .build();
    }

    @Test
    @DisplayName("관리자 ID 헤더 없이 현재 개발 관리자를 Controller에 전달한다")
    void resolveArgument_withoutAdminHeader_injectsDevelopmentAdmin() throws Exception {
        // Given
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").value(ADMIN_ID.toString()));

        verify(adminActorResolver).resolve();
    }

    @Test
    @DisplayName("가짜 관리자 ID 헤더를 보내도 개발 관리자의 ID를 전달한다")
    void resolveArgument_withForgedAdminHeader_ignoresHeader() throws Exception {
        // Given
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/test")
                        .header(
                                "X-Admin-Id",
                                UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").value(ADMIN_ID.toString()));

        verify(adminActorResolver).resolve();
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/admin/test")
        AuthenticatedAdmin getCurrentAdmin(
                @CurrentAdmin AuthenticatedAdmin authenticatedAdmin
        ) {
            return authenticatedAdmin;
        }
    }
}
