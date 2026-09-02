package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.repository.AdminUserQueryCriteria;
import com.chalkak.backend.admin.repository.AdminUserQueryRepository;
import com.chalkak.backend.admin.repository.AdminUserQuerySort;
import com.chalkak.backend.admin.repository.AdminUserQueryStatus;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserQueryService {

    private static final int MAX_EMAIL_FILTER_LENGTH = 320;

    private final AdminUserQueryRepository adminUserQueryRepository;
    private final ImageUrlProvider imageUrlProvider;

    public AdminUserListResult getUsers(
            AdminUserStatus status,
            String email,
            AdminUserSort sort,
            int page,
            int pageSize
    ) {
        validatePagination(page, pageSize);
        String normalizedEmail = normalizeEmail(email);
        AdminUserQueryCriteria criteria = new AdminUserQueryCriteria(
                toQueryStatus(status),
                normalizedEmail,
                toQuerySort(sort));
        return AdminUserListResult.from(
                adminUserQueryRepository.findUsers(criteria, page, pageSize));
    }

    public AdminUserDetail getUser(UUID userId) {
        return adminUserQueryRepository.findUserById(userId)
                .map(user -> AdminUserDetail.from(user, imageUrlProvider))
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "사용자를 찾을 수 없습니다."));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > MAX_EMAIL_FILTER_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이메일 검색어가 올바르지 않습니다.");
        }
        return normalizedEmail;
    }

    private void validatePagination(int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1
                || pageSize < 1
                || pageSize > 100
                || offset > Integer.MAX_VALUE) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "조회 조건이 올바르지 않습니다.");
        }
    }

    private AdminUserQueryStatus toQueryStatus(AdminUserStatus status) {
        if (status == null) {
            return null;
        }
        return AdminUserQueryStatus.valueOf(status.name());
    }

    private AdminUserQuerySort toQuerySort(AdminUserSort sort) {
        if (sort == AdminUserSort.CREATED_AT_ASC) {
            return AdminUserQuerySort.CREATED_AT_ASC;
        }
        return AdminUserQuerySort.CREATED_AT_DESC;
    }
}
