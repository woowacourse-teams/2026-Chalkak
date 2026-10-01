package com.chalkak.backend.admin.infrastructure.persistence.topic;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.topic.AdminTopicProjection;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryCriteria;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryRepository;
import com.chalkak.backend.admin.repository.topic.AdminTopicSort;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminTopicQueryRepositoryImpl implements AdminTopicQueryRepository {

    private final AdminTopicJpaRepository repository;

    @Override
    public AdminPage<AdminTopicProjection> findTopics(
            AdminTopicQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        PageRequest pageable = PageRequest.of(page - 1, pageSize, toSort(criteria.sort()));
        Slice<AdminTopicProjection> result = repository.findTopics(criteria, pageable);
        return new AdminPage<>(result.getContent(), page, pageSize, result.hasNext());
    }

    @Override
    public Optional<AdminTopicProjection> findActiveTopicById(UUID id) {
        return repository.findActiveTopicDetail(id, PageRequest.of(0, 1)).stream().findFirst();
    }

    private Sort toSort(AdminTopicSort sort) {
        Objects.requireNonNull(sort);
        if (sort == AdminTopicSort.TOPIC_DATE_ASC) {
            return Sort.by(Sort.Direction.ASC, "topicDate", "id");
        }
        if (sort == AdminTopicSort.CREATED_AT_DESC) {
            return Sort.by(Sort.Direction.DESC, "createdAt", "id");
        }
        if (sort == AdminTopicSort.CREATED_AT_ASC) {
            return Sort.by(Sort.Direction.ASC, "createdAt", "id");
        }
        return Sort.by(Sort.Direction.DESC, "topicDate", "id");
    }
}
