package com.chalkak.backend.analytics.repository;

/**
 * AI 리뷰 일별 집계 결과.
 *
 * @param users
 *            요청한 서로 다른 회원 수
 * @param requests
 *            최초 요청된 리뷰 수
 * @param completed
 *            그중 집계 시점에 완료 상태인 수
 * @param failed
 *            그중 집계 시점에 실패 상태인 수
 */
public record AiReviewCounts(
        long users,
        long requests,
        long completed,
        long failed
) {
}
