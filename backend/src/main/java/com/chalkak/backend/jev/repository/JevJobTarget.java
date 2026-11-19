package com.chalkak.backend.jev.repository;

import java.util.UUID;

/** 잠금 전에 식별자만 읽어 엔티티의 오래된 상태를 다시 사용하지 않는다. */
public record JevJobTarget(UUID postId, boolean preview) {
}
