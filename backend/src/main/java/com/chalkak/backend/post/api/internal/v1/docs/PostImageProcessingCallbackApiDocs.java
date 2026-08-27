package com.chalkak.backend.post.api.internal.v1.docs;

import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.post.api.internal.v1.dto.response.PostProcessingUploadUrlsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Internal Post Image Processing", description = "게시물 이미지 처리 내부 콜백 API")
public interface PostImageProcessingCallbackApiDocs {

    @Operation(
            summary = "게시물 이미지 처리 결과 업로드 URL 발급",
            description = "Lambda가 원본과 썸네일을 저장할 S3 presigned PUT URL을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 URL 발급 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 콜백 서명",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PostProcessingUploadUrlsResponse> issueUploadUrls(
            @Parameter(
                    description = "처리할 게시물 이미지 업로드 ID",
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
                    schema = @Schema(type = "string", format = "uuid")
            )
            String uploadId,
            @Parameter(
                    name = "X-Chalkak-Callback-Timestamp",
                    description = "요청 시각의 Unix epoch 초. 서버 시각과 5분 이내여야 합니다.",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "1787562000"
            )
            String timestamp,
            @Parameter(
                    name = "X-Chalkak-Callback-Signature",
                    description = "timestamp, HTTP 메서드, 경로, 본문 해시를 서명한 v1 HMAC-SHA256 값",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "v1=0123456789abcdef"
            )
            String signature
    );

    @Operation(
            summary = "게시물 이미지 처리 완료 콜백",
            description = """
                    Lambda의 게시물 이미지 변환 완료 결과를 멱등하게 반영합니다.
                    업로드를 READY로 올리고, 이미 만들어진 검수 중 게시물이 있으면 공개 상태로 승격합니다.
                    본문의 EXIF는 S3 객체에서 제거되고 DB에만 남습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "완료 결과 반영 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "읽을 수 없는 콜백 본문",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 콜백 서명",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> complete(
            @Parameter(
                    description = "처리한 게시물 이미지 업로드 ID",
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
                    schema = @Schema(type = "string", format = "uuid")
            )
            String uploadId,
            @Parameter(
                    name = "X-Chalkak-Callback-Timestamp",
                    description = "요청 시각의 Unix epoch 초. 서버 시각과 5분 이내여야 합니다.",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "1787562000"
            )
            String timestamp,
            @Parameter(
                    name = "X-Chalkak-Callback-Signature",
                    description = "timestamp, HTTP 메서드, 경로, 본문 해시를 서명한 v1 HMAC-SHA256 값",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "v1=0123456789abcdef"
            )
            String signature,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "이미지 처리 결과와 추출한 EXIF",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation =
                                            com.chalkak.backend.post.api.internal.v1.dto.request
                                                    .PostImageProcessingCompleteRequest.class
                            )
                    )
            )
            byte[] rawBody
    );

    @Operation(
            summary = "게시물 이미지 처리 실패 콜백",
            description = """
                    Lambda의 게시물 이미지 변환 실패 결과를 멱등하게 반영합니다.
                    업로드를 REJECTED로 내리고, 이미 만들어진 검수 중 게시물이 있으면 함께 반려합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "실패 결과 반영 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "읽을 수 없는 콜백 본문",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 콜백 서명",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> fail(
            @Parameter(
                    description = "처리한 게시물 이미지 업로드 ID",
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
                    schema = @Schema(type = "string", format = "uuid")
            )
            String uploadId,
            @Parameter(
                    name = "X-Chalkak-Callback-Timestamp",
                    description = "요청 시각의 Unix epoch 초. 서버 시각과 5분 이내여야 합니다.",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "1787562000"
            )
            String timestamp,
            @Parameter(
                    name = "X-Chalkak-Callback-Signature",
                    description = "timestamp, HTTP 메서드, 경로, 본문 해시를 서명한 v1 HMAC-SHA256 값",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "v1=0123456789abcdef"
            )
            String signature,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "거절 사유",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation =
                                            com.chalkak.backend.post.api.internal.v1.dto.request
                                                    .PostImageProcessingFailRequest.class
                            )
                    )
            )
            byte[] rawBody
    );
}
