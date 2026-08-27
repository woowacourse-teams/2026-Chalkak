package com.chalkak.backend.admin.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(AdminArgumentResolverFailClosedWebMvcTest.TestController.class)
@Import({
        AdminArgumentResolverFailClosedWebMvcTest.TestController.class,
        AdminArgumentResolverWebMvcConfig.class,
        GlobalExceptionHandler.class
})
class AdminArgumentResolverFailClosedWebMvcTest {

    private static final String FORGED_ADMIN_ID =
            "0198f6c1-62ba-7d30-8b12-0f733b6570f6";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("작업자 전략이 누락되어도 요청 값으로 관리자 ID를 위조하지 못한다")
    void request_withoutAdminActorResolver_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test")
                        .queryParam("adminId", FORGED_ADMIN_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("관리자 API에 접근할 수 없습니다."));
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/v1/admin/test")
        public AuthenticatedAdmin getCurrentAdmin(
                @CurrentAdmin AuthenticatedAdmin authenticatedAdmin
        ) {
            return authenticatedAdmin;
        }
    }
}
