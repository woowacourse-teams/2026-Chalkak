package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackQueryRepository;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSort;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSummaryProjection;
import java.util.UUID;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminFeedbackQueryRepositoryImpl implements AdminFeedbackQueryRepository {

    private final AdminFeedbackJpaRepository repository;

    @Override
    public Optional<AdminFeedbackSummaryProjection> findFeedbackById(UUID feedbackId) {
        return repository.findFeedbackById(feedbackId);
    }

    @Override
    public AdminPage<AdminFeedbackSummaryProjection> findFeedbacks(
            UUID userId,
            AdminFeedbackSort sort,
            int page,
            int pageSize
    ) {
        PageRequest pageable = PageRequest.of(
                page - 1,
                pageSize,
                toSort(sort)
        );
        Slice<AdminFeedbackSummaryProjection> result = repository.findFeedbacks(userId, pageable);
        return new AdminPage<>(
                result.getContent(),
                page,
                pageSize,
                result.hasNext()
        );
    }

    private Sort toSort(AdminFeedbackSort sort) {
        if (sort == AdminFeedbackSort.CREATED_AT_ASC) {
            return Sort.by(
                    Sort.Direction.ASC,
                    "createdAt",
                    "id"
            );
        }
        return Sort.by(
                Sort.Direction.DESC,
                "createdAt",
                "id"
        );
    }
}
