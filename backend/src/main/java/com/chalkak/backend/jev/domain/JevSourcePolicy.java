package com.chalkak.backend.jev.domain;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;

public final class JevSourcePolicy {

    private JevSourcePolicy() {
    }

    public static String findHoldReason(Post post) {
        if (post == null || post.getDeletedAt() != null || post.getPhoto().getDeletedAt() != null
                || post.getTopic().getDeletedAt() != null || post.getAuthor().isDeleted()) {
            return "SOURCE_DELETED";
        }
        if (post.getModerationStatus() == ModerationStatus.VALIDATING) {
            return "IMAGE_NOT_READY";
        }
        if (post.getModerationStatus() == ModerationStatus.REJECTED
                && post.getModeratedAt() == null) {
            return "IMAGE_PROCESSING_FAILED";
        }
        if (post.getModerationStatus() != ModerationStatus.PENDING) {
            return "ADMIN_ALREADY_DECIDED";
        }
        return null;
    }
}
