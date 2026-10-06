package com.chalkak.backend.notification.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.notification.service.NotificationDetailResult;
import com.chalkak.backend.notification.service.NotificationListResult;
import com.chalkak.backend.support.WithMockLoginUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    private static final String USER_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6572a1";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID NOTIFICATION_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6572f2");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("알림 목록은 페이지 정보와 썸네일을 반환한다")
    void getNotifications_returnsPaginatedNotifications() throws Exception {
        given(notificationService.getNotifications(USER_ID, 2, 10))
                .willReturn(new NotificationListResult(
                        2,
                        10,
                        false,
                        List.of(new NotificationListResult.Summary(
                                NOTIFICATION_ID,
                                NotificationType.POST_REJECTED,
                                "게시물이 반려되었습니다.",
                                "반려 사유를 확인해 주세요.",
                                "https://cdn.test/thumbnail.webp",
                                null,
                                Instant.parse("2026-09-29T09:00:00Z")))));

        mockMvc.perform(get("/api/v1/notifications")
                .param("page", "2")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.notifications[0].id")
                        .value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.notifications[0].thumbnailImageUrl")
                        .value("https://cdn.test/thumbnail.webp"));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("알림 상세는 원본 사진과 반려 사유를 반환하고 조회만으로 읽음 처리하지 않는다")
    void getNotification_returnsOriginalAndRejectionReasonWithoutMarkingRead() throws Exception {
        given(notificationService.getNotification(USER_ID, NOTIFICATION_ID))
                .willReturn(new NotificationDetailResult(
                        NOTIFICATION_ID,
                        NotificationType.POST_REJECTED,
                        "게시물이 반려되었습니다.",
                        "반려 사유를 확인해 주세요.",
                        "https://cdn.test/original.webp",
                        "사진 품질",
                        null,
                        Instant.parse("2026-09-29T09:00:00Z")));

        mockMvc.perform(get("/api/v1/notifications/{notificationId}", NOTIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalImageUrl")
                        .value("https://cdn.test/original.webp"))
                .andExpect(jsonPath("$.rejectionReason").value("사진 품질"));

        verify(notificationService, never()).markRead(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("미읽음 상태는 hasUnread 필드로 반환한다")
    void getUnreadStatus_returnsHasUnread() throws Exception {
        given(notificationService.hasUnreadNotification(USER_ID)).willReturn(true);

        mockMvc.perform(get("/api/v1/notifications/unread-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnread").value(true));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("알림 읽음 요청은 본인 알림 ID로 처리하고 204를 반환한다")
    void markRead_returnsNoContent() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", NOTIFICATION_ID))
                .andExpect(status().isNoContent());

        verify(notificationService).markRead(USER_ID, NOTIFICATION_ID);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("모두 읽기 요청은 현재 회원에게 적용하고 204를 반환한다")
    void markAllRead_returnsNoContent() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(USER_ID);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("목록 페이지 크기가 범위를 벗어나면 400을 반환한다")
    void getNotifications_invalidPageSize_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("pageSize", "101"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).getNotifications(any(), any(Integer.class),
                any(Integer.class));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("다른 회원의 알림 상세는 404를 반환한다")
    void getNotification_otherUser_returnsNotFound() throws Exception {
        given(notificationService.getNotification(USER_ID, NOTIFICATION_ID))
                .willThrow(new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "알림을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/v1/notifications/{notificationId}", NOTIFICATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알림을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("인증 정보가 없으면 알림함 조회를 거부한다")
    void getNotifications_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
