package com.chalkak.backend.admin.api.v1.dto.request;

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
        AiPromptParametersRequest parameters,
        @NotBlank
        @Schema(
                description = "프롬프트를 실행할 공개 게시물 ID. 승인되었고 삭제되지 않은 게시물이어야 합니다.",
                type = "string",
                format = "uuid")
        String postId) {
}
