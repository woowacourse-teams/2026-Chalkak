package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.post.domain.ModerationStatus;

public enum AdminPostListStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public ModerationStatus toModerationStatus() {
        return ModerationStatus.valueOf(name());
    }
}
