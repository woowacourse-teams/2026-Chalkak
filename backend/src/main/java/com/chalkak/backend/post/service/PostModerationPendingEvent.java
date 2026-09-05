package com.chalkak.backend.post.service;

import java.time.Instant;
import java.util.UUID;

/** 게시물이 관리자 검수 대기 상태로 전환됐다는 사실만 전달한다. */
public record PostModerationPendingEvent(UUID postId, Instant occurredAt) {
}
