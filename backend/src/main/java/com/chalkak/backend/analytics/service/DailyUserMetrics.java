package com.chalkak.backend.analytics.service;

import java.time.LocalDate;

/**
 * 한 KST 날짜의 사용자 지표.
 *
 * <p>
 * {@code dau}(그날 게시물을 올린 회원 수), {@code posts}(그날 생성된 게시물 수), {@code wau}(그날까지
 * 7일 동안 게시물을 올린 회원 수)는 게시물 생성 시각으로 정해지므로 같은 날짜를 다시 집계해도 같다.
 * {@code eligibleUsers}(활동 가능한 회원 수)와 {@code likesTotal}(삭제되지 않은 게시물의 좋아요 수)은
 * 집계를 돌린 시점의 스냅샷이라 다시 돌리면 달라질 수 있다.
 */
public record DailyUserMetrics(
        LocalDate metricDate,
        long dau,
        long posts,
        long wau,
        long eligibleUsers,
        long likesTotal
) {
}
