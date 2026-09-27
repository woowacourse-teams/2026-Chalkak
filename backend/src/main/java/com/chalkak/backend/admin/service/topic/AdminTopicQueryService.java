package com.chalkak.backend.admin.service.topic;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.topic.AdminTopicProjection;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryCriteria;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryRepository;
import com.chalkak.backend.admin.repository.topic.AdminTopicSort;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.topic.domain.TopicPhase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTopicQueryService {

    private final AdminTopicQueryRepository adminTopicQueryRepository;
    private final Clock clock;

    public AdminTopicListResult getTopics(
            TopicPhase phase,
            LocalDate dateFrom,
            LocalDate dateTo,
            AdminTopicSort sort,
            int page,
            int pageSize
    ) {
        validateQuery(dateFrom, dateTo, sort, page, pageSize);
        Instant now = clock.instant();
        AdminPage<AdminTopicProjection> result = adminTopicQueryRepository.findTopics(
                new AdminTopicQueryCriteria(
                        phase,
                        dateFrom,
                        dateTo,
                        sort,
                        now),
                page,
                pageSize);
        List<AdminTopicDetail> topics = result.items().stream()
                .map(topic -> AdminTopicDetail.from(topic, now))
                .toList();
        return new AdminTopicListResult(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                topics);
    }

    public AdminTopicDetail getTopic(UUID topicId) {
        return getTopic(topicId, clock.instant());
    }

    AdminTopicDetail getTopic(UUID topicId, Instant now) {
        if (topicId == null || now == null) {
            throw invalidRequestException();
        }
        return adminTopicQueryRepository.findActiveTopicById(topicId)
                .map(topic -> AdminTopicDetail.from(topic, now))
                .orElseThrow(this::topicNotFoundException);
    }

    private void validateQuery(
            LocalDate dateFrom,
            LocalDate dateTo,
            AdminTopicSort sort,
            int page,
            int pageSize
    ) {
        long offset = ((long) page - 1) * pageSize;
        if (sort == null
                || page < 1
                || pageSize < 1
                || pageSize > 100
                || offset > Integer.MAX_VALUE
                || (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo))) {
            throw invalidRequestException();
        }
    }

    private BusinessException invalidRequestException() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "관리자 주제 요청이 올바르지 않습니다.");
    }

    private NotFoundException topicNotFoundException() {
        return new NotFoundException(
                ErrorCode.BUSINESS_ERROR,
                "주제를 찾을 수 없습니다.");
    }
}
