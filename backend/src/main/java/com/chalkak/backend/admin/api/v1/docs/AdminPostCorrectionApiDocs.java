package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminPostCorrectionRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminPostModerationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Post Correction")
public interface AdminPostCorrectionApiDocs {

    @Operation(summary = "확정된 게시물 검수 결과 정정", description = "APPROVED↔REJECTED 정정 전용이며 양방향 사유가 필수입니다. 삭제된 사진·이미지 처리 실패는 정정하지 않고 이전 반려 사진 외 다른 활성 게시물이 있으면 승인하지 않습니다. 같은 현재 상태 요청은 변경/알림 없이 반환하며 이 경우 moderatedBy/rejectionReason은 null입니다. 평가 정답은 바꾸지 않습니다.")
    ResponseEntity<AdminPostModerationResponse> correct(
            @Parameter(hidden = true) AuthenticatedAdmin admin,
            String postId,
            AdminPostCorrectionRequest request
    );
}
