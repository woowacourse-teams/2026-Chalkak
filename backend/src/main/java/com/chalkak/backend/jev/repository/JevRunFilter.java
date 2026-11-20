package com.chalkak.backend.jev.repository;

import com.chalkak.backend.jev.domain.JevDisposition;
import java.util.UUID;

public record JevRunFilter(
        Boolean preview,
        UUID postId,
        UUID topicId,
        JevDisposition disposition
) {
}
