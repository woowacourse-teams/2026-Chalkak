package com.chalkak.backend.admin.service.feedback;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackQueryRepository;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSort;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFeedbackQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminFeedbackQueryRepository adminFeedbackQueryRepository;

    public AdminFeedbackListResult getFeedbacks(
            AdminFeedbackSort sort,
            int page,
            int pageSize
    ) {
        validatePagination(page, pageSize);
        return AdminFeedbackListResult.from(
                adminFeedbackQueryRepository.findFeedbacks(
                        sort == null ? AdminFeedbackSort.CREATED_AT_DESC : sort, page, pageSize));
    }

    private void validatePagination(int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1
                || pageSize < 1
                || pageSize > MAX_PAGE_SIZE
                || offset > Integer.MAX_VALUE) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "조회 조건이 올바르지 않습니다.");
        }
    }

}
