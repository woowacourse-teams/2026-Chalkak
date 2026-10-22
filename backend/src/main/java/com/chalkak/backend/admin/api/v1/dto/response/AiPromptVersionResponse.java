package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.VersionResult;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AiPromptVersionResponse(
        UUID promptVersionId,
        AiPromptType type,
        @Schema(description = "같은 type 안에서 1부터 하나씩 늘어나는 버전 번호. 가장 큰 번호가 최신입니다.")
        int version,
        String systemPrompt,
        String userPromptTemplate,
        String model,
        ParametersResponse parameters,
        @Schema(description = "모델이 지켜야 할 출력 스키마 버전", example = "1")
        String outputSchemaVersion,
        @Schema(description = "관리자용 메모. 없으면 null입니다.", nullable = true)
        String memo,
        UUID createdByAdminId,
        Instant createdAt
) {

    public static AiPromptVersionResponse from(VersionResult result) {
        return new AiPromptVersionResponse(
                result.id(),
                result.type(),
                result.version(),
                result.systemPrompt(),
                result.userPromptTemplate(),
                result.model(),
                ParametersResponse.from(result.parameters()),
                result.outputSchemaVersion(),
                result.memo(),
                result.createdByAdminId(),
                result.createdAt()
        );
    }

    public record ParametersResponse(
            ImageDetail imageDetail,
            Integer maxOutputTokens,
            @Schema(description = "생략했으면 null이며 모델 기본값을 사용합니다.", nullable = true)
            ReasoningEffort reasoningEffort,
            Double distinctivePerspectiveThreshold
    ) {

        private static ParametersResponse from(AiPromptParameters parameters) {
            return new ParametersResponse(
                    parameters.imageDetail(),
                    parameters.maxOutputTokens(),
                    parameters.reasoningEffort(),
                    parameters.distinctivePerspectiveThreshold()
            );
        }
    }
}
