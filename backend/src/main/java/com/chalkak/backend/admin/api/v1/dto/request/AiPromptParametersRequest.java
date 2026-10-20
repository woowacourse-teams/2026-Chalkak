package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import io.swagger.v3.oas.annotations.media.Schema;

public record AiPromptParametersRequest(
        @Schema(description = "이미지 상세도. 생략하면 HIGH.")
        ImageDetail imageDetail,
        @Schema(description = "최대 출력 토큰(1~16000). 추론 토큰도 포함됩니다. 생략하면 8000.")
        Integer maxOutputTokens,
        @Schema(description = "추론 강도. 생략하면 모델 기본값을 사용합니다.")
        ReasoningEffort reasoningEffort,
        @Schema(description = "주제 표현 점수가 이 값 이상이면 독특한 시각으로 판정하는 기준(0~10). 생략하면 7.0.")
        Double distinctivePerspectiveThreshold
) {

    /**
     * parameters 객체를 통째로 생략한 요청은 모든 항목을 생략한 것과 같게 본다.
     */
    public static AiPromptParametersRequest orDefaults(AiPromptParametersRequest parameters) {
        return parameters == null
                ? new AiPromptParametersRequest(
                        null,
                        null,
                        null,
                        null
                )
                : parameters;
    }
}
