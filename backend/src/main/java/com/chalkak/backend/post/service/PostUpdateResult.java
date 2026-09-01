package com.chalkak.backend.post.service;

import java.util.UUID;

public record PostUpdateResult(
        UUID postId,
        String title
) {
}
