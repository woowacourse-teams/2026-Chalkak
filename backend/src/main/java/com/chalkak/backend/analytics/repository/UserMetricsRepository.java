package com.chalkak.backend.analytics.repository;

import java.time.Instant;

/**
 * 사용자 지표 집계용 조회. 지표에서 제외할 회원은 구현체가 걸러낸다.
 */
public interface UserMetricsRepository {

    /** 구간 {@code [from, to)}에 게시물을 만든 회원 수. 삭제됐거나 거절된 게시물도 센다. */
    long countPostAuthors(
            Instant from,
            Instant to
    );

    /** 구간 {@code [from, to)}에 만들어진 게시물 수. 삭제됐거나 거절된 게시물도 센다. */
    long countPosts(
            Instant from,
            Instant to
    );

    /** 코호트 구간에 게시물을 만든 회원 중 복귀 구간에도 게시물을 만든 회원 수. 두 구간 모두 {@code [from, to)}이다. */
    long countReturningPostAuthors(
            Instant cohortFrom,
            Instant cohortTo,
            Instant returnFrom,
            Instant returnTo
    );

    /** 탈퇴하지 않았고 정지되지 않은 회원 수. */
    long countEligibleUsers();

    /** 삭제되지 않은 게시물에 달린 좋아요 수. */
    long countLikesOnLivePosts();

    /**
     * 최초 요청 시각이 구간 {@code [from, to)}인 AI 리뷰의 요청 회원 수, 요청 수, 현재 상태가 완료·실패인 수. 재요청은
     * 최초 요청 시각을 바꾸지 않으므로 세지 않는다.
     */
    AiReviewCounts countAiReviews(
            Instant from,
            Instant to
    );

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
    record AiReviewCounts(
            long users,
            long requests,
            long completed,
            long failed
    ) {
    }
}
