package com.chalkak.backend.notification.api.v1.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.service.PushDeviceService;
import com.chalkak.backend.support.WithMockLoginUser;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PushDeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PushDeviceControllerTest {

    private static final String USER_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6572a1";
    private static final String SESSION_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6572f2";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID SESSION_ID = UUID.fromString(SESSION_ID_VALUE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushDeviceService pushDeviceService;

    @Test
    @WithMockLoginUser(value = USER_ID_VALUE, sessionId = SESSION_ID_VALUE)
    @DisplayName("회원과 로그인 ID는 JWT에서 꺼내 기기를 등록하고 본문 없는 204를 반환한다")
    void registerCurrentDevice_validRequest_returnsNoContent() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/push-devices/current")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fcmToken":"fcm-token"}
                        """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(pushDeviceService).register(USER_ID, SESSION_ID, "fcm-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"fcmToken\":null}", "{\"fcmToken\":\"\"}",
            "{\"fcmToken\":\"   \"}", "{", ""})
    @WithMockLoginUser(value = USER_ID_VALUE, sessionId = SESSION_ID_VALUE)
    @DisplayName("토큰이 없거나 비어 있거나 JSON이 잘못되면 400 오류로 응답한다")
    void registerCurrentDevice_invalidRequest_returnsBadRequest(String body) throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/push-devices/current")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        verifyNoInteractions(pushDeviceService);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("로그인 ID가 없는 JWT의 재로그인 오류를 401로 반환한다")
    void registerCurrentDevice_missingSession_returnsReauthenticationRequired() throws Exception {
        // Given
        willThrow(new UnauthorizedException(ErrorCode.REAUTHENTICATION_REQUIRED, "다시 로그인해 주세요."))
                .given(pushDeviceService).register(USER_ID, null, "fcm-token");

        // When & Then
        mockMvc.perform(put("/api/v1/push-devices/current")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fcmToken":"fcm-token"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REAUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("다시 로그인해 주세요."));
    }

    @Test
    @WithMockLoginUser(value = USER_ID_VALUE, sessionId = SESSION_ID_VALUE)
    @DisplayName("서비스의 탈퇴 회원 오류를 401로 반환한다")
    void registerCurrentDevice_withdrawnUser_returnsUnauthorized() throws Exception {
        // Given
        willThrow(new UnauthorizedException(ErrorCode.UNAUTHORIZED, "탈퇴한 회원입니다."))
                .given(pushDeviceService).register(USER_ID, SESSION_ID, "fcm-token");

        // When & Then
        mockMvc.perform(put("/api/v1/push-devices/current")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fcmToken":"fcm-token"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
}
