package com.chalkak.backend.admin.repository.feedback;

import java.util.UUID;

public record AdminFeedbackQueryCriteria(
        UUID userId,
        InquiryStatus inquiryStatus,
        boolean bannedAuthorOnly
) {

    public enum InquiryStatus {
        ALL, NEEDS_ANSWER, ANSWERED, INQUIRY_DELETED
    }
}
