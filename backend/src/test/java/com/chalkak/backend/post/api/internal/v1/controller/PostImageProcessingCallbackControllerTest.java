package com.chalkak.backend.post.api.internal.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.api.support.ProcessingCallbackAuthenticator;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.post.repository.PostProcessingImageUpload;
import com.chalkak.backend.post.service.PostCommandService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostImageProcessingCallbackController.class)
@Import(GlobalExceptionHandler.class)
class PostImageProcessingCallbackControllerTest {

    private static final String TIMESTAMP_HEADER = "X-Chalkak-Callback-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Chalkak-Callback-Signature";
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final String COMPLETE_BODY = """
            {"width":4032,"height":3024,"byteSize":812345,\
            "location":{"latitude":37.5665,"longitude":126.978},\
            "capturedAt":"2026-08-20T11:02:31+09:00",\
            "metaAttributes":{"Model":"iPhone 15 Pro"}}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostCommandService postCommandService;

    @MockitoBean
    private ProcessingCallbackAuthenticator authenticator;

    @Test
    @DisplayName("인증된 Lambda에 게시물 처리 결과용 presigned URL을 발급한다")
    void issueUploadUrls_authenticatedRequest_returnsPresignedUrls() throws Exception {
        // Given
        given(postCommandService.issuePostImageProcessingUpload(UPLOAD_ID))
                .willReturn(new PostProcessingImageUpload(
                        "https://s3.test/original",
                        "https://s3.test/thumbnail",
                        "image/webp",
                        "public, max-age=86400"
                ));

        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/upload-urls", UPLOAD_ID)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUploadUrl").value("https://s3.test/original"))
                .andExpect(jsonPath("$.thumbnailUploadUrl").value("https://s3.test/thumbnail"))
                .andExpect(jsonPath("$.contentType").value("image/webp"))
                .andExpect(jsonPath("$.cacheControl").value("public, max-age=86400"));

        verify(authenticator).authenticate(
                "/internal/v1/post-image-processing/" + UPLOAD_ID + "/upload-urls",
                null,
                "1787562000",
                "signature"
        );
    }

    @Test
    @DisplayName("업로드 URL 발급 서명이 유효하지 않으면 URL을 발급하지 않고 401을 반환한다")
    void issueUploadUrls_invalidSignature_returnsUnauthorized() throws Exception {
        // Given
        doThrow(new UnauthorizedException(
                ErrorCode.UNAUTHORIZED,
                "유효하지 않은 이미지 처리 콜백입니다."
        ))
                .when(authenticator)
                .authenticate(any(), any(), any(), any());

        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/upload-urls", UPLOAD_ID)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "invalid"))
                .andExpect(status().isUnauthorized());

        verify(postCommandService, never()).issuePostImageProcessingUpload(any());
    }

    @Test
    @DisplayName("인증된 완료 콜백은 이미지 메타데이터를 반영하고 204를 반환한다")
    void complete_authenticatedCallback_completesProcessing() throws Exception {
        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/complete", UPLOAD_ID)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_BODY))
                .andExpect(status().isNoContent());

        verify(authenticator).authenticate(
                "/internal/v1/post-image-processing/" + UPLOAD_ID + "/complete",
                COMPLETE_BODY.getBytes(StandardCharsets.UTF_8),
                "1787562000",
                "signature"
        );
        verify(postCommandService).completePostImageProcessing(eq(UPLOAD_ID), any(Map.class));
    }

    @Test
    @DisplayName("인증된 실패 콜백은 거절 사유를 반영하고 204를 반환한다")
    void fail_authenticatedCallback_failsProcessing() throws Exception {
        // Given
        String body = "{\"reason\":\"UNSUPPORTED_FORMAT\"}";

        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/failed", UPLOAD_ID)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(authenticator).authenticate(
                "/internal/v1/post-image-processing/" + UPLOAD_ID + "/failed",
                body.getBytes(StandardCharsets.UTF_8),
                "1787562000",
                "signature"
        );
        verify(postCommandService).failPostImageProcessing(UPLOAD_ID, "UNSUPPORTED_FORMAT");
    }

    @Test
    @DisplayName("서명이 유효하지 않으면 상태를 바꾸지 않고 401을 반환한다")
    void complete_invalidSignature_returnsUnauthorized() throws Exception {
        // Given
        doThrow(new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 이미지 처리 콜백입니다."))
                .when(authenticator)
                .authenticate(any(), any(), any(), any());

        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/complete", UPLOAD_ID)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_BODY))
                .andExpect(status().isUnauthorized());

        verify(postCommandService, never()).completePostImageProcessing(any(), any());
    }

    @Test
    @DisplayName("인증 헤더가 없으면 400이 아니라 401로 다룬다")
    void complete_missingHeaders_returnsUnauthorized() throws Exception {
        // Given
        doThrow(new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 이미지 처리 콜백입니다."))
                .when(authenticator)
                .authenticate(any(), any(), any(), any());

        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/complete", UPLOAD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_BODY))
                .andExpect(status().isUnauthorized());

        verify(postCommandService, never()).completePostImageProcessing(any(), any());
    }

    @Test
    @DisplayName("거절 사유가 비어 있는 실패 콜백은 400을 반환하고 상태를 바꾸지 않는다")
    void fail_blankReason_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/failed", UPLOAD_ID)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(postCommandService, never()).failPostImageProcessing(any(), any());
    }

    @Test
    @DisplayName("범위를 벗어난 좌표가 담긴 완료 콜백은 400을 반환한다")
    void complete_coordinateOutOfRange_returnsBadRequest() throws Exception {
        // Given
        String body = """
                {"width":4032,"height":3024,"byteSize":812345,\
                "location":{"latitude":91.0,"longitude":126.978}}""";

        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/complete", UPLOAD_ID)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(postCommandService, never()).completePostImageProcessing(any(), any());
    }

    @Test
    @DisplayName("한도를 넘는 촬영 시각이 담긴 완료 콜백은 400을 반환한다")
    void complete_oversizedCapturedAt_returnsBadRequest() throws Exception {
        // Given
        String body = "{\"capturedAt\":\"" + "A".repeat(65) + "\"}";

        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/complete", UPLOAD_ID)
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(postCommandService, never()).completePostImageProcessing(any(), any());
    }

    @Test
    @DisplayName("정규 형식이 아닌 uploadId는 400을 반환하고 인증까지 가지 않는다")
    void complete_nonCanonicalUploadId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/internal/v1/post-image-processing/{uploadId}/complete", "1-1-1-1-1")
                        .header(TIMESTAMP_HEADER, "1787562000")
                        .header(SIGNATURE_HEADER, "signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_BODY))
                .andExpect(status().isBadRequest());

        verify(authenticator, never()).authenticate(any(), any(), any(), any());
        verify(postCommandService, never()).completePostImageProcessing(any(), any());
    }
}
