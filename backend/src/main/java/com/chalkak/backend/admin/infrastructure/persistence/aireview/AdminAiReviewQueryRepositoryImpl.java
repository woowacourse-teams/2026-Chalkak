package com.chalkak.backend.admin.infrastructure.persistence.aireview;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminAiReviewQueryRepositoryImpl implements AdminAiReviewQueryRepository {

    private final AdminAiReviewJpaRepository repository;

    @Override
    public AdminPage<SummaryProjection> findAiReviews(
            Criteria criteria,
            int page,
            int pageSize
    ) {
        Slice<SummaryProjection> result = repository.findAiReviews(
                criteria,
                PageRequest.of(page - 1, pageSize)
        );
        return new AdminPage<>(
                result.getContent(),
                page,
                pageSize,
                result.hasNext()
        );
    }
}
