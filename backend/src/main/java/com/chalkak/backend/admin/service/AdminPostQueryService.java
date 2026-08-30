package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.repository.AdminPostQueryCriteria;
import com.chalkak.backend.admin.repository.AdminPostQueryRepository;
import com.chalkak.backend.admin.repository.AdminPostQuerySort;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostQueryService {

    private final AdminPostQueryRepository adminPostQueryRepository;
    private final ImageUrlProvider imageUrlProvider;

    public AdminPostListResult getPosts(
            ModerationStatus status,
            UUID topicId,
            LocalDate topicDate,
            UUID userId,
            Instant createdAtFrom,
            Instant createdAtTo,
            AdminPostSort sort,
            int page,
            int pageSize
    ) {
        validateStatus(status);
        validatePagination(page, pageSize);
        validateCreatedAtRange(createdAtFrom, createdAtTo);
        AdminPostQueryCriteria criteria = new AdminPostQueryCriteria(
                status,
                topicId,
                topicDate,
                userId,
                createdAtFrom,
                createdAtTo,
                toQuerySort(sort)
        );

        return AdminPostListResult.from(
                adminPostQueryRepository.findPosts(criteria, page, pageSize),
                imageUrlProvider
        );
    }

    public AdminPostDetail getPost(UUID postId) {
        return adminPostQueryRepository.findPostById(postId)
                .map(post -> AdminPostDetail.from(post, imageUrlProvider))
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));
    }

    private void validateStatus(ModerationStatus status) {
        if (status != ModerationStatus.VALIDATING) {
            return;
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "게시물 조회 상태가 올바르지 않습니다."
        );
    }

    private void validateCreatedAtRange(Instant createdAtFrom, Instant createdAtTo) {
        if (createdAtFrom != null
                && createdAtTo != null
                && createdAtFrom.isAfter(createdAtTo)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "등록 시각 범위가 올바르지 않습니다."
            );
        }
    }

    private void validatePagination(int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1
                || pageSize < 1
                || pageSize > 100
                || offset > Integer.MAX_VALUE) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "조회 조건이 올바르지 않습니다."
            );
        }
    }

    private AdminPostQuerySort toQuerySort(AdminPostSort sort) {
        if (sort == AdminPostSort.CREATED_AT_ASC) {
            return AdminPostQuerySort.CREATED_AT_ASC;
        }
        return AdminPostQuerySort.CREATED_AT_DESC;
    }
}
