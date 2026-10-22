package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.notification.AdminReminderListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 리마인더 목록. 등록 최신순으로 정렬합니다.")
public record AdminReminderListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminReminderResponse> reminders
) {

    public static AdminReminderListResponse from(AdminReminderListResult result) {
        return new AdminReminderListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.reminders().stream()
                        .map(AdminReminderResponse::from)
                        .toList()
        );
    }
}
