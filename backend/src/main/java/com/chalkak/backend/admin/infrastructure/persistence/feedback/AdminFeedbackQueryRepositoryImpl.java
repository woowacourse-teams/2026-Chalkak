package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackQueryRepository;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSort;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackProjection;
import java.util.UUID;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminFeedbackQueryRepositoryImpl implements AdminFeedbackQueryRepository {

    private final AdminFeedbackJpaRepository repository;

    @Override
    public Optional<AdminFeedbackProjection> findFeedbackById(UUID feedbackId) {
        return repository.findFeedbackById(feedbackId);
    }

    @Override
    public AdminPage<AdminFeedbackProjection> findFeedbacks(
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
        Slice<AdminFeedbackProjection> result = findFeedbackSlice(userId, pageable);
        return new AdminPage<>(
                result.getContent(),
                page,
                pageSize,
                result.hasNext()
        );
    }

    private Slice<AdminFeedbackProjection> findFeedbackSlice(UUID userId,
            Pageable pageable) {
        if (userId == null) {
            return repository.findFeedbacks(pageable);
        }
        return repository.findFeedbacksByUserId(userId, pageable);
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
