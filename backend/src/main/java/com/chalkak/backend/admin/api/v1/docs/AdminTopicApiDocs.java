package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminTopicDeletionRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminTopicListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminTopicMutationRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminTopicDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminTopicListResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Topics", description = "관리자 주제 생명주기 API")
public interface AdminTopicApiDocs {

    @Operation(summary = "관리자 주제 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 조회 조건",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminTopicListResponse> getTopics(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminTopicListRequest request);

    @Operation(summary = "관리자 주제 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 주제 또는 날짜 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminTopicDetailResponse> createTopic(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminTopicMutationRequest request);

    @Operation(summary = "관리자 주제 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 주제 ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "주제를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminTopicDetailResponse> getTopic(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(schema = @Schema(type = "string", format = "uuid")) String topicId);

    @Operation(summary = "관리자 주제 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공", useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 주제·상태 또는 날짜 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "주제를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminTopicDetailResponse> updateTopic(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(schema = @Schema(type = "string", format = "uuid")) String topicId,
            AdminTopicMutationRequest request);

    @Operation(summary = "관리자 주제 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 상태 또는 삭제 사유",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "주제를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteTopic(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(schema = @Schema(type = "string", format = "uuid")) String topicId,
            AdminTopicDeletionRequest request);
}
