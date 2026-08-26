package com.chalkak.backend.post.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.post.api.v1.dto.request.PostListRequest;
import com.chalkak.backend.post.api.v1.dto.response.PostDetailResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
            summary = "게시물 목록 조회",
            description = "인증 정보가 없으면 isLiked는 false입니다. 랜덤 정렬의 다음 페이지 요청에는 최초 응답의 randomSeed를 사용합니다."
    )
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
            @Parameter(
                    name = "X-User-Id",
                    description = "로그인 사용자의 좋아요 여부 조회용 임시 사용자 ID",
                    in = ParameterIn.HEADER,
                    required = false,
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570a1",
                    schema = @Schema(type = "string", format = "uuid")
            )
            Optional<AuthenticatedUser> loginUser
    );

    @Operation(summary = "게시물 상세 조회")
    @SecurityRequirement(name = "userIdHeader")
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
