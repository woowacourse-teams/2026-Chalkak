package com.chalkak.backend.post.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.post.api.v1.dto.request.PostCreateRequest;
import com.chalkak.backend.post.api.v1.dto.response.PostCreateResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostImageUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Posts", description = "게시물 API")
public interface PostCreationApiDocs {

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
    @SecurityRequirement(name = "userIdHeader")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
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
                    responseCode = "404",
                    description = "사진을 업로드할 회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PostImageUploadResponse> createPostImageUpload(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(
            summary = "게시물 생성",
            description = "업로드된 사진과 선택 제목을 주제에 연결하고 검수를 시작합니다."
    )
    @SecurityRequirement(name = "userIdHeader")
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
                    responseCode = "404",
                    description = "회원, 주제 또는 업로드 사진을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PostCreateResponse> createPost(
            @Parameter(hidden = true) AuthenticatedUser loginUser,
            PostCreateRequest request
    );
}
