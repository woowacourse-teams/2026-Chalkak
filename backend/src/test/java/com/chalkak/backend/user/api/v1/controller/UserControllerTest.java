package com.chalkak.backend.user.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.service.UserSignatureResult;
import com.chalkak.backend.user.service.UserService;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    private static final String USER_ID_VALUE =
            "0198f6c1-62ba-7d30-8b12-0f733b6570b7";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("탈퇴에 성공하면 204를 반환한다")
    void withdraw_validRequest_returnsNoContent() throws Exception {
        // Given
        UUID userId = USER_ID;
        willDoNothing().given(userService).withdraw(userId);

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isNoContent());

        verify(userService).withdraw(userId);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("탈퇴할 회원이 없으면 401을 반환한다")
    void withdraw_notExistingUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = USER_ID;
        willThrow(new UnauthorizedException(
                ErrorCode.UNAUTHORIZED,
                "유효하지 않은 인증 정보입니다."))
                .given(userService).withdraw(userId);

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));
    }

    @Test
    @DisplayName("인증 정보가 없으면 401을 반환한다")
    void withdraw_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/users/me"))
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
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("쿼리 파라미터를 함께 보내도 헤더의 식별자로 탈퇴한다")
    void withdraw_bothHeaderAndQueryParameter_usesHeader() throws Exception {
        // Given
        UUID userId = USER_ID;
        UUID victimId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .param("userId", victimId.toString()))
                .andExpect(status().isNoContent());

        verify(userService).withdraw(userId);
        verify(userService, never()).withdraw(victimId);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("사인 업로드 URL 발급에 성공하면 업로드 정보를 반환한다")
    void createSignatureUpload_validRequest_returnsUploadInformation() throws Exception {
        // Given
        UUID userId = USER_ID;
        UUID uploadId = UUID.randomUUID();
        String uploadUrl = "https://s3.example.com/presigned";
        given(userService.createSignatureUpload(userId))
                .willReturn(new SignatureImageUpload(uploadId, uploadUrl, 300L));

        // When & Then
        mockMvc.perform(post("/api/v1/users/me/signature/uploads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.uploadUrl").value(uploadUrl))
                .andExpect(jsonPath("$.expiresInSeconds").value(300L));

        verify(userService).createSignatureUpload(userId);
    }

    @Test
    @DisplayName("사인 업로드 URL 발급에 사용자 식별 헤더가 없으면 401을 반환한다")
    void createSignatureUpload_missingUserIdHeader_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/users/me/signature/uploads"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(userService, never()).createSignatureUpload(any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("사인을 업로드할 회원이 없으면 401을 반환한다")
    void createSignatureUpload_notExistingUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = USER_ID;
        willThrow(new UnauthorizedException(
                ErrorCode.UNAUTHORIZED,
                "유효하지 않은 인증 정보입니다."))
                .given(userService).createSignatureUpload(userId);

        // When & Then
        mockMvc.perform(post("/api/v1/users/me/signature/uploads"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message")
                        .value("유효하지 않은 인증 정보입니다."));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("내 사인 조회에 성공하면 현재 사인의 원본과 썸네일 이미지 URL을 반환한다")
    void getSignature_validRequest_returnsOriginalAndThumbnailImageUrls() throws Exception {
        // Given
        UUID userId = USER_ID;
        String originalImageUrl = "https://cdn.example.com/signatures/original/current.png";
        String thumbnailImageUrl = "https://cdn.example.com/signatures/thumbnail/current.png";
        given(userService.getSignature(userId))
                .willReturn(new UserSignatureResult(originalImageUrl, thumbnailImageUrl));

        // When & Then
        mockMvc.perform(get("/api/v1/users/me/signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureOriginalImageUrl").value(originalImageUrl))
                .andExpect(jsonPath("$.signatureThumbnailImageUrl").value(thumbnailImageUrl));

        verify(userService).getSignature(userId);
    }

    @Test
    @DisplayName("내 사인 조회에 인증 정보가 없으면 401을 반환한다")
    void getSignature_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/me/signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(userService, never()).getSignature(any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("사인을 조회할 회원이 없으면 401을 반환한다")
    void getSignature_notExistingUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = USER_ID;
        willThrow(new UnauthorizedException(
                ErrorCode.UNAUTHORIZED,
                "유효하지 않은 인증 정보입니다."))
                .given(userService).getSignature(userId);

        // When & Then
        mockMvc.perform(get("/api/v1/users/me/signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message")
                        .value("유효하지 않은 인증 정보입니다."));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("사인 처리가 실패했으면 재등록용 오류를 반환한다")
    void getSignature_processingFailed_returnsBadRequest() throws Exception {
        // Given
        UUID userId = USER_ID;
        willThrow(new BusinessException(
                ErrorCode.SIGNATURE_REGISTRATION_REQUIRED,
                "사인 이미지 처리에 실패했습니다. 사인을 다시 등록해 주세요."))
                .given(userService).getSignature(userId);

        // When & Then
        mockMvc.perform(get("/api/v1/users/me/signature"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("SIGNATURE_REGISTRATION_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("사인 이미지 처리에 실패했습니다. 사인을 다시 등록해 주세요."));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("사인 교체에 성공하면 이미지 URL을 반환한다")
    void updateSignature_validRequest_returnsImageUrl() throws Exception {
        // Given
        UUID userId = USER_ID;
        UUID uploadId = UUID.randomUUID();
        String imageUrl = "https://cdn.example.com/signatures/dev/original/" + uploadId + ".png";
        given(userService.updateSignature(userId, uploadId)).willReturn(imageUrl);

        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureOriginalImageUrl").value(imageUrl));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("실패한 사인 업로드를 다시 요청하면 이미지 재업로드 오류를 반환한다")
    void updateSignature_failedUpload_returnsReuploadRequired() throws Exception {
        // Given
        UUID userId = USER_ID;
        UUID uploadId = UUID.randomUUID();
        willThrow(new BusinessException(
                ErrorCode.SIGNATURE_REUPLOAD_REQUIRED,
                "사인 이미지 처리에 실패했습니다. "
                        + "새로운 업로드 ID를 발급받아 이미지를 다시 업로드해 주세요."))
                .given(userService).updateSignature(userId, uploadId);

        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("SIGNATURE_REUPLOAD_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("사인 이미지 처리에 실패했습니다. "
                                + "새로운 업로드 ID를 발급받아 이미지를 다시 업로드해 주세요."));
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
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("업로드 식별자가 없으면 400을 반환한다")
    void updateSignature_missingUploadId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("사인 이미지 업로드 정보가 올바르지 않습니다."));

        verify(userService, never()).updateSignature(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("업로드 식별자가 UUID 형식이 아니면 400을 반환한다")
    void updateSignature_invalidUploadId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verify(userService, never()).updateSignature(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("업로드한 사인 이미지를 찾을 수 없으면 404를 반환한다")
    void updateSignature_notUploadedImage_returnsNotFound() throws Exception {
        // Given
        UUID userId = USER_ID;
        UUID uploadId = UUID.randomUUID();
        willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "업로드한 사인 이미지를 찾을 수 없습니다."))
                .given(userService).updateSignature(userId, uploadId);

        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("업로드한 사인 이미지를 찾을 수 없습니다."));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("사용할 수 없는 사인 이미지면 400을 반환한다")
    void updateSignature_disallowedImage_returnsBadRequest() throws Exception {
        // Given
        UUID userId = USER_ID;
        UUID uploadId = UUID.randomUUID();
        willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "사용할 수 없는 사인 이미지입니다."))
                .given(userService).updateSignature(userId, uploadId);

        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signatureOriginalUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("사용할 수 없는 사인 이미지입니다."));
    }
}
