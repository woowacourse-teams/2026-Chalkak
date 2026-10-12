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

    /** 탈퇴하지 않았고 정지되지 않은 회원 수. */
    long countEligibleUsers();

    /** 삭제되지 않은 게시물에 달린 좋아요 수. */
    long countLikesOnLivePosts();
}
