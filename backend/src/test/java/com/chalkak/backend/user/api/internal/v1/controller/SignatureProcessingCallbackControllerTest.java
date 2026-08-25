package com.chalkak.backend.user.api.internal.v1.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.api.support.ProcessingCallbackAuthenticator;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SignatureProcessingCallbackController.class)
@Import(GlobalExceptionHandler.class)
class SignatureProcessingCallbackControllerTest {

    private static final String TIMESTAMP_HEADER = "X-Chalkak-Callback-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Chalkak-Callback-Signature";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ProcessingCallbackAuthenticator authenticator;

    @Test
    @DisplayName("인증된 성공 콜백은 pending 사인을 승격하고 204를 반환한다")
    void complete_authenticatedCallback_completesProcessing() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(post("/internal/v1/signature-processing/{uploadId}/complete", uploadId)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature"))
                .andExpect(status().isNoContent());

        verify(authenticator).authenticate(callbackPath(uploadId, "complete"), null, "1787562000", "signature");
        verify(userService).completeSignatureProcessing(uploadId);
    }

    @Test
    @DisplayName("인증된 실패 콜백은 pending 사인을 실패로 바꾸고 204를 반환한다")
    void fail_authenticatedCallback_failsProcessing() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(post("/internal/v1/signature-processing/{uploadId}/failed", uploadId)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature"))
                .andExpect(status().isNoContent());

        verify(authenticator).authenticate(callbackPath(uploadId, "failed"), null, "1787562000", "signature");
        verify(userService).failSignatureProcessing(uploadId);
    }

    @Test
    @DisplayName("서명이 올바르지 않은 콜백은 401을 반환하고 상태를 변경하지 않는다")
    void complete_invalidSignature_returnsUnauthorized() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        doThrow(new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 이미지 처리 콜백입니다."))
                .when(authenticator)
                .authenticate(callbackPath(uploadId, "complete"), null, "1787562000", "invalid");

        // When & Then
        mockMvc.perform(post("/internal/v1/signature-processing/{uploadId}/complete", uploadId)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "invalid"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).completeSignatureProcessing(uploadId);
    }

    @Test
    @DisplayName("서명 헤더가 없는 콜백은 401을 반환하고 상태를 변경하지 않는다")
    void complete_missingSignatureHeader_returnsUnauthorized() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        doThrow(new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 이미지 처리 콜백입니다."))
                .when(authenticator)
                .authenticate(callbackPath(uploadId, "complete"), null, "1787562000", null);

        // When & Then
        mockMvc.perform(post("/internal/v1/signature-processing/{uploadId}/complete", uploadId)
                        .header(TIMESTAMP_HEADER, "1787562000"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).completeSignatureProcessing(uploadId);
    }

    @Test
    @DisplayName("timestamp 헤더가 없는 콜백은 401을 반환하고 상태를 변경하지 않는다")
    void complete_missingTimestampHeader_returnsUnauthorized() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        doThrow(new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 이미지 처리 콜백입니다."))
                .when(authenticator)
                .authenticate(callbackPath(uploadId, "complete"), null, null, "signature");

        // When & Then
        mockMvc.perform(post("/internal/v1/signature-processing/{uploadId}/complete", uploadId)
                        .header(SIGNATURE_HEADER, "signature"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).completeSignatureProcessing(uploadId);
    }
    private static String callbackPath(UUID uploadId, String result) {
        return "/internal/v1/signature-processing/" + uploadId + "/" + result;
    }
}
