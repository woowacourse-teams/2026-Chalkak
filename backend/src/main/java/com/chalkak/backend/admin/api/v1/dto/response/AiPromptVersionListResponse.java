package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.VersionListResult;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.VersionSummary;
import com.chalkak.backend.aireview.domain.AiPromptType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiPromptVersionListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        @Schema(description = "version 내림차순(최신 먼저). 프롬프트 본문은 상세 조회로 확인합니다.")
        List<PromptVersionItem> promptVersions) {

    public static AiPromptVersionListResponse from(VersionListResult result) {
        return new AiPromptVersionListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.promptVersions().stream().map(PromptVersionItem::from).toList());
    }

    public record PromptVersionItem(
            UUID promptVersionId,
            AiPromptType type,
            @Schema(description = "같은 type 안에서 1부터 하나씩 늘어나는 버전 번호. 가장 큰 번호가 최신입니다.")
            int version,
            String model,
            @Schema(description = "모델이 지켜야 할 출력 스키마 버전", example = "1")
            String outputSchemaVersion,
            @Schema(description = "관리자용 메모. 없으면 null입니다.", nullable = true)
            String memo,
            UUID createdByAdminId,
            Instant createdAt) {

        private static PromptVersionItem from(VersionSummary summary) {
            return new PromptVersionItem(
                    summary.id(),
                    summary.type(),
                    summary.version(),
                    summary.model(),
                    summary.outputSchemaVersion(),
                    summary.memo(),
                    summary.createdByAdminId(),
                    summary.createdAt());
        }
    }
}
