package com.chalkak.backend.aireview.infrastructure.persistence;

import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.repository.AiReviewRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AiReviewRepositoryImpl implements AiReviewRepository {

    private final AiReviewJpaRepository jpaRepository;

    @Override
    public int insertIfAbsent(AiReview review) {
        return jpaRepository.insertIfAbsent(
                review.getPostId(),
                review.getUserId(),
                review.getPromptVersionId(),
                review.getStatus().name(),
                review.getAttempt(),
                review.getRequestId(),
                review.getRequestedAt(),
                review.getLastRequestedAt()
        );
    }

    @Override
    public Optional<AiReview> findByPostId(UUID postId) {
        return jpaRepository.findByPostId(postId);
    }

    @Override
    public Optional<AiReview> findClaimableForUpdate(Instant now) {
        return jpaRepository.findClaimableForUpdate(now);
    }

    @Override
    public Optional<AiReview> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id);
    }
}
