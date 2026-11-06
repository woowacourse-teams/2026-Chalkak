package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackQueryCriteria.InquiryStatus;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminFeedbackListRequest(
        @Schema(description = "작성자 ID. 생략하면 전체, 해당 사용자의 문의가 없으면 빈 목록", format = "uuid")
        String userId,

        @Schema(
                description = "답변 필요는 답변 가능한 미답변·답변 삭제 문의, 답변 완료는 삭제되지 않은 현재 답변 문의, 삭제는 문의 자체의 삭제를 뜻합니다.",
                defaultValue = "ALL")
        InquiryStatus inquiryStatus,

        @Schema(description = "현재 차단된 작성자의 문의만 조회하며 문의 상태 조건과 함께 적용됩니다.", defaultValue = "false")
        Boolean bannedAuthorOnly,

        @Schema(
                description = "접수 시각 정렬",
                defaultValue = "createdAtDesc",
                implementation = String.class,
                allowableValues = {
                        "createdAtDesc", "createdAtAsc"})
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
        inquiryStatus = inquiryStatus == null ? InquiryStatus.ALL : inquiryStatus;
        bannedAuthorOnly = Boolean.TRUE.equals(bannedAuthorOnly);
        sort = sort == null ? DEFAULT_SORT : sort;
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
