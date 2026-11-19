package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.JevPreviewCreateRequest;
import com.chalkak.backend.admin.api.v1.dto.response.JevRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Jev Previews", description = "Jev 초안 시험 실행과 결과 조회")
public interface AdminJevPreviewApiDocs {

    @Operation(summary = "Jev 초안 시험 작업 등록", description = "공개 게시물 사진과 주제로 초안을 시험합니다. "
            + "원문은 시험 작업에만 저장되며 프롬프트 버전·운영 활성화·게시물 상태·사용자 알림을 변경하지 않습니다. "
            + "201의 jobId로 결과를 조회합니다. 같은 주제/분류 초안의 분류 호출은 DB에서 재사용합니다.")
    @ApiResponse(responseCode = "201", description = "시험 작업 등록", useReturnTypeSchema = true)
    ResponseEntity<JevRunResponse> createPreview(
            @Parameter(hidden = true) AuthenticatedAdmin admin,
            JevPreviewCreateRequest request
    );

    @Operation(summary = "Jev 시험 작업 상세", description = "분류와 사진의 호출별 원응답·실패·HTTP 시간, "
            + "입출력 토큰과 질문별 선택·확률·서버 추천을 조회합니다. 공유 분류 토큰은 classificationId 기준으로 한 번만 합산합니다. "
            + "추천은 실제 게시물 최종 결정이 아닙니다. 확인할 수 없는 사용량/지연은 null입니다.")
    @ApiResponse(responseCode = "200", description = "작업 상세", useReturnTypeSchema = true)
    ResponseEntity<JevRunResponse> getPreview(String jobId);
}
