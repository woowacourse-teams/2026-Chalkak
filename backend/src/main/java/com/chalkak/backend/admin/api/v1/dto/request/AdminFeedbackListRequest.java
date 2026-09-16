package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.admin.service.AdminFeedbackSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminFeedbackListRequest(
        @Schema(
                description = "접수 시각 정렬",
                defaultValue = "createdAtDesc",
                implementation = String.class,
                allowableValues = {"createdAtDesc", "createdAtAsc"})
        AdminFeedbackSort sort,

        @Schema(description = "페이지 번호", defaultValue = "1")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

        @Schema(description = "페이지당 피드백 수", defaultValue = "20")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize
) {

    private static final AdminFeedbackSort DEFAULT_SORT = AdminFeedbackSort.CREATED_AT_DESC;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public AdminFeedbackListRequest {
        sort = sort == null ? DEFAULT_SORT : sort;
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
