package com.chalkak.backend.user.api.internal.v1.docs;

import com.chalkak.backend.exception.ErrorResponse;
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

@Tag(name = "Internal Signature Processing", description = "사인 이미지 처리 내부 콜백 API")
public interface SignatureProcessingCallbackApiDocs {

    @Operation(
            summary = "사인 이미지 처리 완료 콜백",
            description = "Lambda의 사인 이미지 변환 완료 결과를 멱등하게 반영합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "완료 결과 반영 성공"),
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
                    description = "처리할 사인 이미지 업로드 ID",
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"
            )
            UUID uploadId,
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
            summary = "사인 이미지 처리 실패 콜백",
            description = "Lambda의 사인 이미지 변환 실패 결과를 멱등하게 반영합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "실패 결과 반영 성공"),
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
                    description = "처리할 사인 이미지 업로드 ID",
                    example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"
            )
            UUID uploadId,
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
}
