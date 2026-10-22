package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.aireview.domain.AiPromptType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiPromptVersionCreateRequest(
        @NotNull
        AiPromptType type,
        @NotBlank
        @Size(max = 50000)
        @Schema(description = "시스템 프롬프트. 변수({{...}})를 쓸 수 없습니다. 최대 50,000자.")
        String systemPrompt,
        @NotBlank
        @Size(max = 2000)
        @Schema(
                description = "사용자 프롬프트 템플릿. PHOTO_REVIEW는 {{topicTitle}} 변수만 쓸 수 있습니다. 최대 2,000자.",
                example = "{\"주제\": \"{{topicTitle}}\"}")
        String userPromptTemplate,
        @NotBlank
        @Size(max = 100)
        @Schema(description = "호출할 모델. 서버가 허용한 모델이어야 합니다.", example = "gpt-6.1-sol")
        String model,
        @Valid
        @Schema(description = "모델 호출 옵션. 생략하면 모든 항목이 기본값입니다.")
        AiPromptParametersRequest parameters,
        @Schema(
                description = "관리자용 메모. 앞뒤 공백을 제거하며 비어 있으면 저장하지 않습니다. "
                        + "공백을 제거한 뒤 글자 수로 최대 500자입니다.",
                maxLength = 500,
                nullable = true)
        String memo
) {
}
