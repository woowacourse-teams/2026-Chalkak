package com.chalkak.backend.user.api.v1.controller;

import static org.hamcrest.Matchers.aMapWithSize;
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
import com.chalkak.backend.user.service.UserWithdrawalService;
import com.chalkak.backend.support.WithMockLoginUser;
import com.chalkak.backend.post.service.AuthorPostListResult;
import com.chalkak.backend.post.service.PostQueryService;
import java.time.LocalDate;
import java.util.List;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    private static final String USER_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6570b7";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID AUTHOR_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserWithdrawalService userWithdrawalService;

    @MockitoBean
    private PostQueryService postQueryService;

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("작성자의 사진·게시물 ID·주제만 응답으로 제공한다")
    void getAuthorPosts_validAuthor_returnsMinimalResponse() throws Exception {
        // Given
        UUID postId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        given(postQueryService.getAuthorPosts(AUTHOR_ID, 1, 20, USER_ID))
                .willReturn(new AuthorPostListResult(1, 20, false,
                        List.of(new AuthorPostListResult.PostSummary(
                                postId, "https://cdn.example.com/original.webp", null,
                                new AuthorPostListResult.TopicSummary(topicId, "오늘의 하늘",
                                        LocalDate.of(2026, 4, 1))))));

        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/posts", AUTHOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(aMapWithSize(4)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.posts[0]").value(aMapWithSize(4)))
                .andExpect(jsonPath("$.posts[0].id").value(postId.toString()))
                .andExpect(jsonPath("$.posts[0].originalImageUrl")
                        .value("https://cdn.example.com/original.webp"))
                .andExpect(jsonPath("$.posts[0].thumbnailImageUrl").isEmpty())
                .andExpect(jsonPath("$.posts[0].topic").value(aMapWithSize(3)))
                .andExpect(jsonPath("$.posts[0].topic.id").value(topicId.toString()))
                .andExpect(jsonPath("$.posts[0].topic.title").value("오늘의 하늘"))
                .andExpect(jsonPath("$.posts[0].topic.topicDate").value("2026-04-01"));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("요청한 페이지와 크기를 전달하고 다음 페이지 여부를 응답한다")
    void getAuthorPosts_secondPage_returnsPaginationMetadata() throws Exception {
        // Given
        given(postQueryService.getAuthorPosts(AUTHOR_ID, 2, 1, USER_ID))
                .willReturn(new AuthorPostListResult(2, 1, true, List.of()));

        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/posts", AUTHOR_ID)
                .param("page", "2")
                .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @ParameterizedTest
    @WithMockLoginUser(USER_ID_VALUE)
    @ValueSource(strings = {"0", "-1", "invalid"})
    @DisplayName("1보다 작거나 숫자가 아닌 페이지 번호를 거절한다")
    void getAuthorPosts_invalidPage_returnsBadRequest(String page) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/posts", AUTHOR_ID).param("page", page))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @ParameterizedTest
    @WithMockLoginUser(USER_ID_VALUE)
    @ValueSource(ints = {1, 2, 99, 100})
    @DisplayName("허용 범위의 조회 개수를 서비스에 전달한다")
    void getAuthorPosts_validPageSize_passesRequestedSize(int pageSize) throws Exception {
        // Given
        given(postQueryService.getAuthorPosts(AUTHOR_ID, 1, pageSize, USER_ID))
                .willReturn(new AuthorPostListResult(1, pageSize, false, List.of()));

        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/posts", AUTHOR_ID)
                .param("pageSize", Integer.toString(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts").isEmpty());
    }

    @ParameterizedTest
    @WithMockLoginUser(USER_ID_VALUE)
    @ValueSource(strings = {"0", "101", "invalid"})
    @DisplayName("허용 범위 밖이거나 숫자가 아닌 조회 개수를 거절한다")
    void getAuthorPosts_invalidPageSize_returnsBadRequest(String pageSize) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/posts", AUTHOR_ID)
                .param("pageSize", pageSize))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @ParameterizedTest
    @WithMockLoginUser(USER_ID_VALUE)
    @ValueSource(strings = {"invalid", "1-1-1-1-1"})
    @DisplayName("잘못된 작성자 ID와 축약 UUID를 거절한다")
    void getAuthorPosts_invalidAuthorId_returnsBadRequest(String authorId) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/posts", authorId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("작성자를 찾을 수 없으면 공통 404 응답을 반환한다")
    void getAuthorPosts_missingAuthor_returnsNotFound() throws Exception {
        // Given
        given(postQueryService.getAuthorPosts(AUTHOR_ID, 1, 20, USER_ID))
                .willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "작성자를 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/posts", AUTHOR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("작성자를 찾을 수 없습니다."));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("조회자 인증 예외를 공통 401 응답으로 변환한다")
    void getAuthorPosts_invalidViewer_returnsUnauthorized() throws Exception {
        // Given
        given(postQueryService.getAuthorPosts(AUTHOR_ID, 1, 20, USER_ID))
                .willThrow(new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."));

        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/posts", AUTHOR_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("탈퇴에 성공하면 204를 반환한다")
    void withdraw_validRequest_returnsNoContent() throws Exception {
        // Given
        UUID userId = USER_ID;
        willDoNothing().given(userWithdrawalService).withdraw(userId);

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isNoContent());

        verify(userWithdrawalService).withdraw(userId);
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
                .given(userWithdrawalService).withdraw(userId);

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

        verify(userWithdrawalService, never()).withdraw(any());
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

        verify(userWithdrawalService, never()).withdraw(any());
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

        verify(userWithdrawalService).withdraw(userId);
        verify(userWithdrawalService, never()).withdraw(victimId);
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
