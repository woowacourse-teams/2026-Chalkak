package com.chalkak.backend.jev.api.internal.v1.docs;

import com.chalkak.backend.jev.api.internal.v1.dto.response.JevJobClaimResponse;
import com.chalkak.backend.jev.api.internal.v1.dto.request.JevJobClaimRequest;
import com.chalkak.backend.jev.api.internal.v1.dto.request.JevJobResultRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Internal Jev Jobs", description = "환경/경로/본문을 묶은 기존 HMAC 인증")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "본문·단계·ID 검증 실패 또는 발급하지 않은 호출"),
        @ApiResponse(responseCode = "401", description = "HMAC·시각·환경·원문 서명 불일치"),
        @ApiResponse(responseCode = "404", description = "작업 또는 분류 없음")
})
public interface JevJobCallbackApiDocs {

    @Operation(summary = "Jev 단계 호출 확보", description = "원문 JSON jobId를 검증한 후 CLASSIFY/INSPECT/WAIT/STOP을 반환합니다. "
            + "callId·단계별 deadline·명시 모델·질문을 고정합니다. WAIT이면 DB 재전달에 맡기고 Lambda를 종료합니다.")
    @ApiResponse(responseCode = "200", description = "호출 소유권 또는 대기/중단", useReturnTypeSchema = true)
    @RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = JevJobClaimRequest.class)))
    ResponseEntity<JevJobClaimResponse> claim(
            @Parameter(name = "X-Chalkak-Callback-Timestamp", in = ParameterIn.HEADER, required = true) String timestamp,
            @Parameter(name = "X-Chalkak-Callback-Signature", in = ParameterIn.HEADER, required = true) String signature,
            @Parameter(hidden = true) byte[] rawBody
    );

    @Operation(summary = "Jev 물리 호출 결과 수신", description = "callId·stage(CLASSIFICATION/PHOTO)·rawResponse·errorCode·httpStatus·latencyMs·retryAfterSeconds를 받습니다. "
            + "서버 발급 callId만 허용하고 완료 호출 재전달은 부수 효과를 중복 생성하지 않습니다. "
            + "시각은 서버가 기록하며 HTTP 시간만 Lambda 측 실제 Jev 왕복 값입니다.")
    @ApiResponse(responseCode = "204", description = "결과 저장 또는 멱등 재전달")
    @RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = JevJobResultRequest.class)))
    ResponseEntity<Void> receiveResult(
            String jobId,
            @Parameter(name = "X-Chalkak-Callback-Timestamp", in = ParameterIn.HEADER, required = true) String timestamp,
            @Parameter(name = "X-Chalkak-Callback-Signature", in = ParameterIn.HEADER, required = true) String signature,
            @Parameter(hidden = true) byte[] rawBody
    );
}
