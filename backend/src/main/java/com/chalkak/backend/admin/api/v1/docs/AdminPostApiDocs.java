package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminPostListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminPostDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminPostListResponse;
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

@Tag(name = "Admin Posts", description = "관리자 게시물 검수 조회 API")
public interface AdminPostApiDocs {

    @Operation(
            summary = "관리자 게시물 목록 조회",
            description = "모든 검수 상태와 삭제된 게시물을 필터링해 조회합니다. 등록 시각 범위의 양 끝값은 포함됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 게시물 목록 조회 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 필터 또는 페이지 조건",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminPostListResponse> getPosts(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminPostListRequest request
    );

    @Operation(
            summary = "관리자 게시물 상세 조회",
            description = "삭제 여부와 검수 상태에 관계없이 게시물과 작성자·주제·사진·이미지 처리 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 게시물 상세 조회 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 게시물 ID",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시물을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminPostDetailResponse> getPost(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(
                    description = "게시물 ID",
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
                    schema = @Schema(type = "string", format = "uuid")
            )
            String postId
    );
}
