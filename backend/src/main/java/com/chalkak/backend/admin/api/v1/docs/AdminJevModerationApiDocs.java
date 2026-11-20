package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.JevEvaluationRequest;
import com.chalkak.backend.admin.api.v1.dto.request.JevHistoryRequest;
import com.chalkak.backend.admin.api.v1.dto.response.JevModerationDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.JevModerationHistoryResponse;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import com.chalkak.backend.jev.domain.JevMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Jev Moderation", description = "운영 검수 기록과 정답 평가. 실제 판정 정정은 별도 API")
public interface AdminJevModerationApiDocs {

    @Operation(summary = "검수 이력 목록", description = "미완료·수동·삭제·시험 기록을 포함합니다. preview 미지정은 양쪽 모두, page 기본0/pageSize 기본20·최대100입니다. 원응답은 상세에서 조회합니다.")
    ResponseEntity<JevModerationHistoryResponse> list(JevHistoryRequest request);

    @Operation(summary = "검수 이력 상세", description = "원래 모델 선택/확률, 서버 추천, 운영 처분과 현재 게시물 상태를 구분합니다. 토큰/HTTP 시간은 물리 호출별 값이며 공유 분류는 classificationId당 한 번만 합산합니다. 미제공은 null이며 추천 문구는 실제 반려 확정 사유가 아닙니다.")
    ResponseEntity<JevModerationDetailResponse> get(String jobId);

    @Operation(summary = "검수 평가 정답 등록·수정", description = "완료된 기록의 정답과 감사 이력만 저장합니다. 공개 상태와 사용자 알림은 변경하지 않습니다. questions는 생략한 질문의 정답을 추정하지 않도록 빈 객체/일부 질문을 허용합니다. 표본 유형과 근거는 필수입니다.")
    ResponseEntity<JevGoldLabel> evaluate(
            @Parameter(hidden = true) AuthenticatedAdmin admin,
            String jobId,
            JevEvaluationRequest request
    );

    @Operation(summary = "검수 성능·사용량·HTTP p99", description = "등록 시각 from 이상/to 미만·최대31일, preview 기본false입니다. 최대10000건을 집계하고 초과하면 truncated=true이므로 기간을 줄여 다시 조회합니다. 무작위/오류 분석 표본을 구분하고 분모·결측치를 함께 반환합니다. 일관성은 같은 사진/주제/설정의 정상 결과가 5회 이상인 그룹으로 계산하며 부족하면 null입니다. p99는 실제 관측된 개별 Jev HTTP 시간의 nearest-rank이며 단계별 p99를 더하지 않습니다. 큐·S3·변환·콜백 시간은 제외합니다.")
    ResponseEntity<JevMetrics> metrics(
            Instant from,
            Instant to,
            Boolean preview
    );
}
