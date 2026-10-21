package com.chalkak.backend.aireview.repository;

import com.chalkak.backend.aireview.domain.AiReview;
import java.time.Instant;
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

    /**
     * 가져갈 수 있는 가장 오래 기다린 행 하나를 잠가서 읽는다. 다른 트랜잭션이 잠근 행은 건너뛰어 워커끼리 같은 행을 가져가지 않는다. 다음
     * 시도 시각이 없거나 지난 대기 행과 임대가 끝난 처리 중 행이 후보이며, 호출한 트랜잭션 안에서 바로 상태를 바꿔야 한다. 기다린 시간은
     * 다음 시도 시각이 있으면 그 시각, 없으면 이번 회차의 요청 시각부터 센다.
     */
    Optional<AiReview> findClaimableForUpdate(Instant now);

    Optional<AiReview> findByIdForUpdate(UUID id);
}
