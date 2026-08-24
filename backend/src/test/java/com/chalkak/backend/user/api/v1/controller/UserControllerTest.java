package com.chalkak.backend.user.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.BusinessException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
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
    @DisplayName("사용자 식별 헤더가 없으면 401을 반환한다")
    void withdraw_missingUserIdHeader_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(userService, never()).withdraw(any());
    }

    @Test
    @DisplayName("사용자 식별 헤더가 UUID 형식이 아니면 401을 반환한다")
    void withdraw_invalidUserIdHeader_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(USER_ID_HEADER, "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(userService, never()).withdraw(any());
    }

    @Test
    @DisplayName("사용자 식별자를 쿼리 파라미터로 보내면 탈퇴를 시도하지 않는다")
    void withdraw_userIdAsQueryParameter_returnsUnauthorized() throws Exception {
        // Given
        UUID victimId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .param("userId", victimId.toString()))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).withdraw(any());
    }

    @Test
    @DisplayName("쿼리 파라미터를 함께 보내도 헤더의 식별자로 탈퇴한다")
    void withdraw_bothHeaderAndQueryParameter_usesHeader() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(USER_ID_HEADER, userId.toString())
                        .param("userId", victimId.toString()))
                .andExpect(status().isNoContent());

        verify(userService).withdraw(userId);
        verify(userService, never()).withdraw(victimId);
    }

    @Test
    @DisplayName("사인 교체에 성공하면 이미지 URL을 반환한다")
    void updateSignature_validRequest_returnsImageUrl() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        String imageUrl = "https://cdn.example.com/signatures/original/" + uploadId + ".png";
        given(userService.updateSignature(userId, uploadId)).willReturn(imageUrl);

        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .header(USER_ID_HEADER, userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureOriginalImageUrl").value(imageUrl));
    }

    @Test
    @DisplayName("사인 교체에 사용자 식별 헤더가 없으면 401을 반환한다")
    void updateSignature_missingUserIdHeader_returnsUnauthorized() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(userService, never()).updateSignature(any(), any());
    }

    @Test
    @DisplayName("업로드 식별자가 없으면 400을 반환한다")
    void updateSignature_missingUploadId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .header(USER_ID_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("signatureOriginalUploadId: 사인 이미지 업로드 정보가 올바르지 않습니다."));

        verify(userService, never()).updateSignature(any(), any());
    }

    @Test
    @DisplayName("업로드 식별자가 UUID 형식이 아니면 400을 반환한다")
    void updateSignature_invalidUploadId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .header(USER_ID_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verify(userService, never()).updateSignature(any(), any());
    }

    @Test
    @DisplayName("업로드한 사인 이미지를 찾을 수 없으면 404를 반환한다")
    void updateSignature_notUploadedImage_returnsNotFound() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "업로드한 사인 이미지를 찾을 수 없습니다."))
                .given(userService).updateSignature(userId, uploadId);

        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .header(USER_ID_HEADER, userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("업로드한 사인 이미지를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("사용할 수 없는 사인 이미지면 400을 반환한다")
    void updateSignature_disallowedImage_returnsBadRequest() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "사용할 수 없는 사인 이미지입니다."))
                .given(userService).updateSignature(userId, uploadId);

        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .header(USER_ID_HEADER, userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("사용할 수 없는 사인 이미지입니다."));
    }
}
