package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.aireview.domain.AiReview;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record AdminAiReviewListRequest(
        @Schema(description = "AI 리뷰 상태. 생략하면 전체")
        AiReview.Status status,

        @Schema(description = "프롬프트 버전 ID. 생략하면 전체. 없는 ID는 오류가 아니라 빈 목록", format = "uuid")
        UUID promptVersionId,

        @Schema(defaultValue = "1", minimum = "1")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

        @Schema(defaultValue = "20", minimum = "1", maximum = "100")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize
) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public AdminAiReviewListRequest {
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
