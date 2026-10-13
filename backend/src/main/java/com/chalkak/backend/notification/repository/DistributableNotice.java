package com.chalkak.backend.notification.repository;

import java.time.Instant;
import java.util.UUID;

// 배포 대상 공지와, 다음 묶음을 이어서 조회할 위치(예약 시각·ID)
public record DistributableNotice(UUID noticeId, Instant scheduledAt) {
}
