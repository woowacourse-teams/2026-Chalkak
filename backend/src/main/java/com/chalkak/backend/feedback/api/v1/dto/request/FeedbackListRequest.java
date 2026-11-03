package com.chalkak.backend.feedback.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FeedbackListRequest(
        @Schema(description = "페이지 번호", defaultValue = "1")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

        @Schema(description = "페이지당 문의 수", defaultValue = "20")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize
) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public FeedbackListRequest {
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
