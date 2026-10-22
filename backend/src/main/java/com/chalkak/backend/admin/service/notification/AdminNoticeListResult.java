package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.notification.repository.NoticeSlice;
import java.util.List;

public record AdminNoticeListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminNoticeResult> notices
) {

    public static AdminNoticeListResult from(
            NoticeSlice slice,
            int page,
            int pageSize
    ) {
        return new AdminNoticeListResult(
                page,
                pageSize,
                slice.hasNext(),
                slice.notices().stream()
                        .map(AdminNoticeResult::from)
                        .toList()
        );
    }
}
