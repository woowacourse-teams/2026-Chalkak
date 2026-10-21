package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.notification.repository.ReminderSlice;
import java.util.List;

public record AdminReminderListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminReminderResult> reminders
) {

    public static AdminReminderListResult from(
            ReminderSlice slice,
            int page,
            int pageSize
    ) {
        return new AdminReminderListResult(
                page,
                pageSize,
                slice.hasNext(),
                slice.reminders().stream()
                        .map(AdminReminderResult::from)
                        .toList()
        );
    }
}
