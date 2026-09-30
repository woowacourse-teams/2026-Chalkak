package com.chalkak.backend.notification.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.service.NotificationSettingsResult;
import com.chalkak.backend.notification.service.NotificationSettingsService;
import com.chalkak.backend.support.WithMockLoginUser;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationSettingsControllerTest {

    private static final String USER_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6572a1";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationSettingsService notificationSettingsService;

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("저장된 두 수신 설정을 불리언 필드로 반환한다")
    void getSettings_authenticatedUser_returnsSettings() throws Exception {
        // Given
        given(notificationSettingsService.getSettings(USER_ID))
                .willReturn(new NotificationSettingsResult(false, true));

        // When & Then
        mockMvc.perform(get("/api/v1/notification-settings"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.topicPushEnabled").value(false))
                .andExpect(jsonPath("$.moderationPushEnabled").value(true))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("회원 조회에서 인증 오류가 발생하면 공통 401 응답을 반환한다")
    void getSettings_invalidUser_returnsUnauthorized() throws Exception {
        // Given
        given(notificationSettingsService.getSettings(USER_ID))
                .willThrow(new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."));

        // When & Then
        mockMvc.perform(get("/api/v1/notification-settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));
    }
}
