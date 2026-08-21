package com.chalkak.backend.user.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.AuthWebConfig;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, AuthWebConfig.class})
class UserControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("탈퇴에 성공하면 204를 반환한다")
    void withdraw_validRequest_returnsNoContent() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        willDoNothing().given(userService).withdraw(userId);

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(USER_ID_HEADER, userId.toString()))
                .andExpect(status().isNoContent());

        verify(userService).withdraw(userId);
    }

    @Test
    @DisplayName("탈퇴할 회원이 없으면 404를 반환한다")
    void withdraw_notExistingUser_returnsNotFound() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "탈퇴할 회원을 찾을 수 없습니다."))
                .given(userService).withdraw(userId);

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(USER_ID_HEADER, userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("탈퇴할 회원을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("사용자 식별 헤더가 없으면 탈퇴를 시도하지 않는다")
    void withdraw_missingUserIdHeader_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("X-User-Id 헤더가 필요합니다."));

        verify(userService, never()).withdraw(any());
    }

    @Test
    @DisplayName("사용자 식별 헤더가 UUID 형식이 아니면 탈퇴를 시도하지 않는다")
    void withdraw_invalidUserIdHeader_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(USER_ID_HEADER, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("X-User-Id 헤더 형식이 올바르지 않습니다."));

        verify(userService, never()).withdraw(any());
    }
}
