package com.chalkak.backend.post.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.post.api.v1.dto.request.PostCalendarRequest;
import com.chalkak.backend.post.api.v1.dto.request.PostCreateRequest;
import com.chalkak.backend.post.api.v1.dto.request.PostListRequest;
import com.chalkak.backend.post.api.v1.dto.response.PostCalendarResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostCreateResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostDetailResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostImageUploadResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Posts", description = "게시물 API")
public interface PostApiDocs {

    @Operation(
            summary = "게시물 이미지 업로드 URL 발급",
            description = """
                    S3에 직접 업로드할 presigned PUT URL을 발급합니다.
                    PUT 요청의 Content-Type은 응답의 `contentType`과 정확히 같아야 합니다.
                    `maxBytes`를 넘는 이미지와 WebP가 아닌 이미지는 업로드 후 이미지 처리
                    단계에서 거절되며, 그 업로드 ID로는 게시물을 만들 수 없습니다.
                    발급받은 `uploadId`는 게시물 생성 요청의 `photoUploadId`로 사용합니다.
                    """
    )
    @SecurityRequirement(name = "accessToken")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "업로드 URL 발급 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "이용이 정지된 회원",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사진을 업로드할 회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PostImageUploadResponse> createPostImageUpload(
            @Parameter(hidden = true) Optional<AuthenticatedUser> loginUser
    );

    @Operation(
            summary = "게시물 생성",
            description = "업로드된 사진과 선택 제목을 주제에 연결합니다. 이미지 처리가 끝나면 관리자 검수 대기 상태가 됩니다."
    )
    @SecurityRequirement(name = "accessToken")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "게시물 생성 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 또는 게시물을 생성할 수 없는 상태",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "이용이 정지된 회원",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원, 주제 또는 업로드 사진을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PostCreateResponse> createPost(
            @Parameter(hidden = true) Optional<AuthenticatedUser> loginUser,
            PostCreateRequest request
    );

    @Operation(
            summary = "본인 게시물 삭제",
            description = """
                    본인이 작성한 PENDING 또는 APPROVED 게시물을 삭제합니다.
                    게시물과 사진은 soft delete되고 연결된 좋아요는 삭제됩니다.
                    이미 삭제한 게시물에 다시 요청해도 성공으로 처리합니다.
                    """
    )
    @SecurityRequirement(name = "accessToken")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "게시물 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 게시물 ID 또는 삭제할 수 없는 상태",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인이 작성한 게시물이 아님",
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
    ResponseEntity<Void> deletePost(
            @Parameter(
                    description = "게시물 ID",
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
                    schema = @Schema(type = "string", format = "uuid")
            )
            String postId,
            @Parameter(hidden = true) Optional<AuthenticatedUser> loginUser
    );

    @Operation(
            summary = "게시물 목록 조회",
            description = "인증 정보가 없으면 isLiked는 false입니다. 랜덤 정렬의 다음 페이지 요청에는 최초 응답의 randomSeed를 사용합니다."
    )
    @SecurityRequirement(name = "accessToken")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시물 목록 조회 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 조회 조건",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 날짜의 주제를 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PostListResponse> getPosts(
            @ParameterObject PostListRequest request,
            @Parameter(hidden = true) Optional<AuthenticatedUser> loginUser
    );

    @Operation(
            summary = "내 게시물 캘린더 조회",
            description = "조회 연월에 작성한 APPROVED 상태의 게시물만 주제 날짜순으로 반환합니다."
    )
    @SecurityRequirement(name = "accessToken")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 게시물 캘린더 조회 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "조회 연월이 올바르지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PostCalendarResponse> getMyPostCalendar(
            @ParameterObject PostCalendarRequest request,
            @Parameter(hidden = true) Optional<AuthenticatedUser> loginUser
    );

    @Operation(summary = "게시물 상세 조회")
    @SecurityRequirement(name = "accessToken")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시물 상세 조회 성공",
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
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
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
    ResponseEntity<PostDetailResponse> getPost(
            @Parameter(
                    description = "게시물 ID",
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"
            )
            String postId,
            @Parameter(hidden = true) Optional<AuthenticatedUser> loginUser
    );
}
