package com.chalkak.backend.admin.infrastructure.persistence.post;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.post.AdminPostDetailProjection;
import com.chalkak.backend.admin.repository.post.AdminPostQueryCriteria;
import com.chalkak.backend.admin.repository.post.AdminPostQueryRepository;
import com.chalkak.backend.admin.repository.post.AdminPostSort;
import com.chalkak.backend.admin.repository.post.AdminPostSummaryProjection;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminPostQueryRepositoryImpl implements AdminPostQueryRepository {

    private final AdminPostJpaRepository repository;

    @Override
    public AdminPage<AdminPostSummaryProjection> findPosts(
            AdminPostQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        PageRequest pageable = PageRequest.of(page - 1, pageSize, toSort(criteria.sort()));
        Slice<AdminPostSummaryProjection> result = repository.findPosts(criteria, pageable);
        return new AdminPage<>(result.getContent(), page, pageSize, result.hasNext());
    }

    @Override
    public Optional<AdminPostDetailProjection> findPostById(UUID id) {
        return repository.findPostDetail(id, PageRequest.of(0, 1)).stream().findFirst();
    }

    private Sort toSort(AdminPostSort sort) {
        if (sort == AdminPostSort.CREATED_AT_ASC) {
            return Sort.by(Sort.Direction.ASC, "createdAt", "id");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt", "id");
    }
}
