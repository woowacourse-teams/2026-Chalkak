package com.chalkak.backend.analytics.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 한 KST 날짜의 사용자 지표.
 *
 * <p>
 * {@code dau}(그날 게시물을 올린 회원 수), {@code posts}(그날 생성된 게시물 수), {@code wau}(그날까지
 * 7일 동안 게시물을 올린 회원 수)는 게시물 생성 시각으로 정해지므로 같은 날짜를 다시 집계해도 같다.
 * {@code eligibleUsers}(활동 가능한 회원 수)와 {@code likesTotal}(삭제되지 않은 게시물의 좋아요 수)은
 * 집계를 돌린 시점의 스냅샷이라 다시 돌리면 달라질 수 있다. {@code accessLogMetrics}는 로그 지표 관측 시작일 이전
 * 날짜에는 {@code null}이다. {@code weeklyRetentions}는 일요일 기준일에만 W+1~W+4 순서로 채우고 다른
 * 요일에는 비어 있다. 게시물 생성 시각으로 정해지므로 {@code dau}, {@code wau}처럼 다시 집계해도 같다.
 */
public record DailyUserMetrics(
        LocalDate metricDate,
        long dau,
        long posts,
        long wau,
        long eligibleUsers,
        long likesTotal,
        AccessLogMetrics accessLogMetrics,
        List<WeeklyRetention> weeklyRetentions) {
}
