package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiPromptPreviewRequest(
        @NotNull
        AiPromptType type,
        @NotBlank
        @Size(max = 50000)
        @Schema(description = "시스템 프롬프트. 변수({{...}})를 쓸 수 없습니다. 최대 50,000자.")
        String systemPrompt,
        @NotBlank
        @Size(max = 2000)
        @Schema(
                description = "사용자 프롬프트 템플릿. PHOTO_REVIEW는 {{topicTitle}} 변수만 쓸 수 있습니다. "
                        + "변수 값은 항상 JSON 문자열 규칙으로 이스케이프해 넣으므로 "
                        + "{\"주제\": \"{{topicTitle}}\"} 형태도 유효한 JSON이 됩니다(평문 템플릿에서도 따옴표는 \\\"로 들어갑니다). 최대 2,000자.",
                example = "{\"주제\": \"{{topicTitle}}\"}")
        String userPromptTemplate,
        @NotBlank
        @Size(max = 100)
        @Schema(description = "호출할 모델. 서버가 허용한 모델이어야 합니다.", example = "gpt-6.1-sol")
        String model,
        @Valid
        @Schema(description = "모델 호출 옵션. 생략하면 모든 항목이 기본값입니다.")
        Parameters parameters,
        @NotBlank
        @Schema(
                description = "프롬프트를 실행할 공개 게시물 ID. 승인되었고 삭제되지 않은 게시물이어야 합니다.",
                type = "string",
                format = "uuid")
        String postId) {

    public record Parameters(
            @Schema(description = "이미지 상세도. 생략하면 HIGH.")
            ImageDetail imageDetail,
            @Schema(description = "최대 출력 토큰(1~16000). 추론 토큰도 포함됩니다. 생략하면 8000.")
            Integer maxOutputTokens,
            @Schema(description = "추론 강도. 생략하면 모델 기본값을 사용합니다.")
            ReasoningEffort reasoningEffort,
            @Schema(description = "주제 표현 점수가 이 값 이상이면 독특한 시각으로 판정하는 기준(0~10). 생략하면 7.0.")
            Double distinctivePerspectiveThreshold) {
    }
}
