package com.chalkak.backend.admin.repository.post;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

public enum AdminPostInstagramConsentFilter {
    AGREED("agreed"), NOT_AGREED("notAgreed"), REVOKED("revoked");

    private final String queryValue;

    AdminPostInstagramConsentFilter(String queryValue) {
        this.queryValue = queryValue;
    }

    public static AdminPostInstagramConsentFilter from(String value) {
        for (AdminPostInstagramConsentFilter filter : values()) {
            if (filter.queryValue.equals(value)) {
                return filter;
            }
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "조회 조건이 올바르지 않습니다.");
    }
}
