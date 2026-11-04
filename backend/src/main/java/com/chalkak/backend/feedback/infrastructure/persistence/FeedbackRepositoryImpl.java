package com.chalkak.backend.feedback.infrastructure.persistence;

import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import com.chalkak.backend.feedback.repository.FeedbackSlice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeedbackRepositoryImpl implements FeedbackRepository {

    private final FeedbackJpaRepository feedbackJpaRepository;

    @Override
    public Feedback save(Feedback feedback) {
        return feedbackJpaRepository.save(feedback);
    }

    @Override
    public Optional<Feedback> findVisibleByIdAndUserId(UUID feedbackId, UUID userId) {
        return feedbackJpaRepository.findByIdAndUserIdAndDeletedAtIsNull(feedbackId, userId);
    }

    @Override
    public Optional<Feedback> findByIdForUpdate(UUID feedbackId) {
        return feedbackJpaRepository.findByIdForUpdate(feedbackId);
    }

    @Override
    public Optional<Feedback> findByIdAndUserIdForUpdate(UUID feedbackId, UUID userId) {
        return feedbackJpaRepository.findByIdAndUserIdForUpdate(feedbackId, userId);
    }

    @Override
    public FeedbackSlice findVisibleByUserId(
            UUID userId,
            int page,
            int pageSize
    ) {
        Slice<Feedback> result = feedbackJpaRepository.findByUserIdAndDeletedAtIsNull(
                userId,
                PageRequest.of(
                        page - 1,
                        pageSize,
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt",
                                "id"
                        )
                )
        );
        return new FeedbackSlice(result.getContent(), result.hasNext());
    }

    @Override
    public List<Instant> findSubmissionTimesAfter(UUID userId, Instant createdAfter) {
        return feedbackJpaRepository.findSubmissionTimesAfter(userId, createdAfter);
    }
}
