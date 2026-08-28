package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.repository.AdminTopicQueryCriteria;
import com.chalkak.backend.admin.repository.AdminTopicQueryPage;
import com.chalkak.backend.admin.repository.AdminTopicQueryRepository;
import com.chalkak.backend.admin.repository.AdminTopicQuerySort;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.TopicPhase;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTopicService {

    private static final int MAX_REASON_LENGTH = 500;

    private final AdminTopicQueryRepository adminTopicQueryRepository;
    private final TopicRepository topicRepository;
    private final AdminAuditLogService adminAuditLogService;
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
        AdminTopicQueryPage result = adminTopicQueryRepository.findTopics(
                new AdminTopicQueryCriteria(
                        phase,
                        dateFrom,
                        dateTo,
                        AdminTopicQuerySort.valueOf(sort.name()),
                        now
                ),
                page,
                pageSize
        );
        List<AdminTopicDetail> topics = result.topics().stream()
                .map(topic -> AdminTopicDetail.from(topic, now))
                .toList();
        return new AdminTopicListResult(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                topics
        );
    }

    public AdminTopicDetail getTopic(UUID topicId) {
        if (topicId == null) {
            throw invalidRequestException();
        }
        return findDetail(topicId, clock.instant());
    }

    @Transactional
    public AdminTopicDetail createTopic(
            UUID adminId,
            String title,
            LocalDate topicDate,
            Instant startsAt,
            Instant endsAt
    ) {
        validateAdminId(adminId);
        Instant now = clock.instant();
        Topic topic = Topic.create(
                title,
                topicDate,
                new ParticipationPeriod(startsAt, endsAt),
                now
        );
        validateDateAvailable(topicDate, null);
        Topic saved = saveUnique(topic);
        createAuditLog(
                adminId,
                AdminAction.TOPIC_CREATED,
                saved,
                null,
                AdminAuditSnapshot.from(Map.of()),
                topicState(saved)
        );
        return findDetail(saved.getId(), now);
    }

    @Transactional
    public AdminTopicDetail updateTopic(
            UUID topicId,
            UUID adminId,
            String title,
            LocalDate topicDate,
            Instant startsAt,
            Instant endsAt
    ) {
        validateIds(topicId, adminId);
        Topic topic = topicRepository.findActiveByIdForUpdate(topicId)
                .orElseThrow(this::topicNotFoundException);
        AdminAuditSnapshot beforeState = topicState(topic);
        Instant now = clock.instant();
        topic.update(
                title,
                topicDate,
                new ParticipationPeriod(startsAt, endsAt),
                now
        );
        validateDateAvailable(topicDate, topicId);
        Topic saved = saveUnique(topic);
        createAuditLog(
                adminId,
                AdminAction.TOPIC_UPDATED,
                saved,
                null,
                beforeState,
                topicState(saved)
        );
        return findDetail(saved.getId(), now);
    }

    @Transactional
    public void deleteTopic(UUID topicId, UUID adminId, String reason) {
        validateIds(topicId, adminId);
        String normalizedReason = normalizeReason(reason);
        Topic topic = topicRepository.findActiveByIdForUpdate(topicId)
                .orElseThrow(this::topicNotFoundException);
        AdminAuditSnapshot beforeState = deletionState(topic);
        topic.delete(clock.instant());
        createAuditLog(
                adminId,
                AdminAction.TOPIC_DELETED,
                topic,
                normalizedReason,
                beforeState,
                deletionState(topic)
        );
    }

    private void validateQuery(
            LocalDate dateFrom,
            LocalDate dateTo,
            AdminTopicSort sort,
            int page,
            int pageSize
    ) {
        if (sort == null
                || page < 1
                || pageSize < 1
                || pageSize > 100
                || (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo))) {
            throw invalidRequestException();
        }
    }

    private void validateIds(UUID topicId, UUID adminId) {
        if (topicId == null || adminId == null) {
            throw invalidRequestException();
        }
    }

    private void validateAdminId(UUID adminId) {
        if (adminId == null) {
            throw invalidRequestException();
        }
    }

    private void validateDateAvailable(LocalDate topicDate, UUID excludedTopicId) {
        boolean duplicated = excludedTopicId == null
                ? topicRepository.existsActiveByTopicDate(topicDate)
                : topicRepository.existsActiveByTopicDateExcludingId(
                        topicDate,
                        excludedTopicId
                );
        if (duplicated) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "해당 날짜의 주제가 이미 존재합니다."
            );
        }
    }

    private Topic saveUnique(Topic topic) {
        try {
            return topicRepository.saveAndFlush(topic);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "해당 날짜의 주제가 이미 존재합니다."
            );
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw invalidRequestException();
        }
        String normalized = reason.trim();
        if (normalized.codePointCount(0, normalized.length()) > MAX_REASON_LENGTH) {
            throw invalidRequestException();
        }
        return normalized;
    }

    private void createAuditLog(
            UUID adminId,
            AdminAction action,
            Topic topic,
            String reason,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                action,
                AdminTargetType.TOPIC,
                topic.getId(),
                reason,
                beforeState,
                afterState,
                UUID.randomUUID()
        ));
    }

    private AdminAuditSnapshot topicState(Topic topic) {
        return AdminAuditSnapshot.from(Map.of(
                "title", topic.getTitle(),
                "topicDate", topic.getTopicDate(),
                "startsAt", topic.getParticipationPeriod().getStartsAt(),
                "endsAt", topic.getParticipationPeriod().getEndsAt()
        ));
    }

    private AdminAuditSnapshot deletionState(Topic topic) {
        Map<String, Object> state = new LinkedHashMap<>(topicState(topic).values());
        state.put("deletedAt", topic.getDeletedAt());
        return AdminAuditSnapshot.from(state);
    }

    private BusinessException invalidRequestException() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "관리자 주제 요청이 올바르지 않습니다."
        );
    }

    private NotFoundException topicNotFoundException() {
        return new NotFoundException(
                ErrorCode.BUSINESS_ERROR,
                "주제를 찾을 수 없습니다."
        );
    }

    private AdminTopicDetail findDetail(UUID topicId, Instant now) {
        return adminTopicQueryRepository.findActiveTopicById(topicId)
                .map(topic -> AdminTopicDetail.from(topic, now))
                .orElseThrow(this::topicNotFoundException);
    }
}
