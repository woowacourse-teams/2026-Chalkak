package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.notification.AdminNoticeListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 공지 목록. 등록 최신순으로 정렬합니다.")
public record AdminNoticeListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminNoticeResponse> notices
) {

    public static AdminNoticeListResponse from(AdminNoticeListResult result) {
        return new AdminNoticeListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.notices().stream()
                        .map(AdminNoticeResponse::from)
                        .toList()
        );
    }
}
