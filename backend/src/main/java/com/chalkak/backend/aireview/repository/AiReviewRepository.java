package com.chalkak.backend.aireview.repository;

import com.chalkak.backend.aireview.domain.AiReview;
import java.util.Optional;
import java.util.UUID;

public interface AiReviewRepository {

    /**
     * 게시물에 리뷰가 없을 때만 대기 상태로 넣는다. 이미 있으면 아무것도 바꾸지 않고, 동시에 요청해도 한 행만 만들어진다.
     *
     * @return 새로 만들었으면 1, 이미 있었으면 0
     */
    int insertIfAbsent(AiReview review);

    Optional<AiReview> findByPostId(UUID postId);
}
