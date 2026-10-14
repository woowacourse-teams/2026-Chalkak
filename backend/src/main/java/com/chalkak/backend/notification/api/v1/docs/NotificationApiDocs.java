package com.chalkak.backend.notification.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.notification.api.v1.dto.request.NotificationListRequest;
import com.chalkak.backend.notification.api.v1.dto.response.NotificationListResponse;
import com.chalkak.backend.notification.api.v1.dto.response.NotificationDetailResponse;
import com.chalkak.backend.notification.api.v1.dto.response.NotificationUnreadStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Notifications", description = "내 알림함 API")
@SecurityRequirement(name = "accessToken")
public interface NotificationApiDocs {

    @Operation(summary = "내 알림 목록 조회", description = "사건 발생 시각부터 30일이 지난 알림과 게시물이 삭제된 알림은 제외합니다. 정확한 30일 경계 시각에는 표시합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 조회 조건", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<NotificationListResponse> getNotifications(
            @ParameterObject NotificationListRequest request,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "내 알림 상세 조회", description = "반려 알림에는 원본 사진과 반려 사유를 포함합니다. 30일이 지난 알림과 삭제된 게시물의 알림은 조회할 수 없습니다. 조회만으로 읽음 처리하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 알림 ID", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<NotificationDetailResponse> getNotification(
            @Parameter(description = "알림 ID", schema = @Schema(type = "string", format = "uuid")) String notificationId,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "내 미읽음 알림 여부 조회", description = "30일이 지나지 않았고 알림함에 표시되는 알림을 기준으로 판단합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<NotificationUnreadStatusResponse> getUnreadStatus(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "내 알림 읽음 처리", description = "30일이 지난 알림과 삭제된 게시물의 알림은 처리할 수 없습니다. 반복 호출해도 처음 읽은 시각을 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "읽음 처리 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 알림 ID", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> markRead(
            @Parameter(description = "알림 ID", schema = @Schema(type = "string", format = "uuid")) String notificationId,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "내 알림 모두 읽음 처리", description = "30일이 지나지 않았고 알림함에 표시되는 미읽음 알림만 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "읽음 처리 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> markAllRead(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );
}
